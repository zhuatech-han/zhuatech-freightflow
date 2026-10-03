// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.freightflow;

import java.math.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 趟次费用独立复核、冻结对账单和原流水冲正；人工登记不发起银行支付。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Service
@Transactional
public class FinanceService {
  final Store db;
  final AccessService access;
  final FreightAccess scope;
  final CommandService commands;
  final TransitService transit;

  public FinanceService(
      Store db,
      AccessService access,
      FreightAccess scope,
      CommandService commands,
      TransitService transit) {
    this.db = db;
    this.access = access;
    this.scope = scope;
    this.commands = commands;
    this.transit = transit;
  }

  /** 司机或运营人员提交实报费用，车次关闭后冻结；财务必须由另一账号复核。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Expense expense(Map<String, Object> v) {
    access.require("expense.write");
    db.lock(Department.class, 1L);
    var t = db.get(Trip.class, FreightPolicy.id(v, "tripId"));
    if (scope.internal()) access.department(t.departmentId);
    else scope.execute(t);
    var cmd = commands.open("EXPENSE_CREATE", v);
    if (!cmd.fresh()) return db.get(Expense.class, cmd.stamp().resultId);
    if (!t.status.equals("IN_TRANSIT")) throw new Problem(409, "INVALID_STATE");
    var e = new Expense();
    e.tripId = t.id;
    e.type = AdminService.text((String) v.get("type"), 30);
    if (!Set.of("FUEL", "TOLL", "PARKING", "OTHER").contains(e.type))
      throw new Problem(400, "INVALID_EXPENSE_TYPE");
    e.amount = FreightPolicy.decimal(v.get("amount"), 2, true);
    e.note = AdminService.text((String) v.get("note"), 800);
    e.reference = FreightPolicy.optional(v.get("reference"), 120);
    e.createdBy = access.current().username;
    e.createdAt = transit.now();
    e.departmentId = t.departmentId;
    db.save(e);
    cmd.stamp().resultId = e.id;
    transit.event(null, t.id, "EXPENSE_SUBMITTED", "", t.departmentId);
    return e;
  }

  /** 费用审核只允许财务且不是填报账号，过期修订拒绝。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Expense reviewExpense(Long id, Map<String, Object> v) {
    access.require("finance");
    internal();
    db.lock(Department.class, 1L);
    var e = db.get(Expense.class, id);
    access.department(e.departmentId);
    var cmd = commands.open("EXPENSE_REVIEW_" + id, v);
    if (!cmd.fresh()) return e;
    transit.rev(e.revision, v);
    if (!e.status.equals("SUBMITTED")) throw new Problem(409, "INVALID_STATE");
    if (e.createdBy.equals(access.current().username))
      throw new Problem(403, "INDEPENDENT_REVIEW_REQUIRED");
    if (!db.get(Trip.class, e.tripId).status.equals("IN_TRANSIT"))
      throw new Problem(409, "INVALID_STATE");
    e.status = Boolean.TRUE.equals(v.get("approved")) ? "APPROVED" : "REJECTED";
    e.reviewNote = AdminService.text((String) v.get("note"), 800);
    e.reviewedBy = access.current().username;
    e.revision++;
    transit.event(null, e.tripId, "EXPENSE_" + e.status, "", e.departmentId);
    return e;
  }

  /** 已签收运单生成客户应收；已关车次生成承运费及实报费用应付，禁止重复计费。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Invoice invoice(Map<String, Object> v) {
    access.require("finance");
    internal();
    db.lock(Department.class, 1L);
    var cmd = commands.open("INVOICE_CREATE", v);
    if (!cmd.fresh()) {
      var x = db.get(Invoice.class, cmd.stamp().resultId);
      access.department(x.departmentId);
      return x;
    }
    var i = new Invoice();
    i.kind = AdminService.text((String) v.get("kind"), 30);
    i.dueDate = LocalDate.parse(String.valueOf(v.get("dueDate")));
    if (i.dueDate.isBefore(transit.now().atZone(transit.zone()).toLocalDate())
        || i.dueDate.isAfter(transit.now().atZone(transit.zone()).toLocalDate().plusDays(365)))
      throw new Problem(400, "INVALID_DUE_DATE");
    var lines = new ArrayList<InvoiceLine>();
    if (i.kind.equals("RECEIVABLE")) {
      var c = db.get(Customer.class, FreightPolicy.id(v, "customerId"));
      access.department(c.departmentId);
      i.customerId = c.id;
      i.partyName = c.name;
      i.departmentId = c.departmentId;
      var ids = ids(v, "shipmentIds");
      for (Long id : ids) {
        var s = db.get(Shipment.class, id);
        if (!s.customerId.equals(c.id) || !s.departmentId.equals(c.departmentId))
          throw new Problem(400, "CUSTOMER_MISMATCH");
        if (!s.status.equals("DELIVERED")) throw new Problem(409, "DELIVERY_INCOMPLETE");
        if (s.invoiceId != null) throw new Problem(409, "ALREADY_INVOICED");
        var l = new InvoiceLine();
        l.shipmentId = s.id;
        l.description = s.code + " / " + s.goods;
        l.amount = s.freight;
        lines.add(l);
      }
    } else if (i.kind.equals("PAYABLE")) {
      var t = db.get(Trip.class, FreightPolicy.id(v, "tripId"));
      access.department(t.departmentId);
      if (!t.status.equals("CLOSED")) throw new Problem(409, "TRIP_NOT_CLOSED");
      if (t.invoiceId != null) throw new Problem(409, "ALREADY_INVOICED");
      i.carrierId = t.carrierId;
      i.tripId = t.id;
      i.partyName = t.carrierName;
      i.departmentId = t.departmentId;
      var base = new InvoiceLine();
      base.description = t.code + " / Freight";
      base.amount = t.carrierFee;
      lines.add(base);
      for (var e :
          db.query(Expense.class, "from Expense where tripId=?1 and status='APPROVED'", t.id)) {
        var l = new InvoiceLine();
        l.description = e.type + " / " + e.reference;
        l.amount = e.amount;
        lines.add(l);
      }
    } else throw new Problem(400, "INVALID_INVOICE_KIND");
    i.code = "B-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
    i.createdAt = transit.now();
    i.createdBy = access.current().username;
    for (var l : lines) i.total = i.total.add(l.amount);
    if (i.total.signum() == 0) i.status = "PAID";
    db.save(i);
    cmd.stamp().resultId = i.id;
    for (var l : lines) {
      l.invoiceId = i.id;
      db.save(l);
      if (l.shipmentId != null) db.get(Shipment.class, l.shipmentId).invoiceId = i.id;
    }
    if (i.tripId != null) db.get(Trip.class, i.tripId).invoiceId = i.id;
    access.audit("INVOICE_CREATE", i.id, i.departmentId);
    return i;
  }

  /** 收付款登记及原流水部分冲正，金额不能超单据余额和原流水可冲正金额。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Invoice action(Long id, String action, Map<String, Object> v) {
    access.require("finance");
    internal();
    db.lock(Department.class, 1L);
    var i = db.get(Invoice.class, id);
    access.department(i.departmentId);
    var cmd = commands.open("INVOICE_" + id + "_" + action, v);
    if (!cmd.fresh()) return i;
    transit.rev(i.revision, v);
    if (i.status.equals("VOID")) throw new Problem(409, "INVALID_STATE");
    var rows = db.query(SettlementEntry.class, "from SettlementEntry where invoiceId=?1", id);
    if (action.equals("void")) {
      if (!rows.isEmpty()) throw new Problem(409, "PAYMENT_EXISTS");
      i.voidReason = AdminService.text((String) v.get("note"), 800);
      i.status = "VOID";
      for (var line : db.query(InvoiceLine.class, "from InvoiceLine where invoiceId=?1", id))
        if (line.shipmentId != null) db.get(Shipment.class, line.shipmentId).invoiceId = null;
      if (i.tripId != null) db.get(Trip.class, i.tripId).invoiceId = null;
    } else {
      var e = new SettlementEntry();
      e.invoiceId = id;
      e.amount = FreightPolicy.decimal(v.get("amount"), 2, true);
      e.reference = AdminService.text((String) v.get("reference"), 120);
      e.actor = access.current().username;
      e.createdAt = transit.now();
      e.departmentId = i.departmentId;
      e.note = FreightPolicy.optional(v.get("note"), 800);
      if (action.equals("pay")) {
        e.kind = "PAYMENT";
        e.method = AdminService.text((String) v.get("method"), 30);
        if (db.query(
                DictionaryEntry.class,
                "from DictionaryEntry where type='payment_method' and code=?1 and enabled=true",
                e.method)
            .isEmpty()) throw new Problem(400, "INVALID_PAYMENT_METHOD");
        if (i.paid.add(e.amount).compareTo(i.total) > 0) throw new Problem(409, "OVERPAYMENT");
        i.paid = i.paid.add(e.amount);
      } else if (action.equals("reverse")) {
        var source = db.get(SettlementEntry.class, FreightPolicy.id(v, "sourceId"));
        if (!source.invoiceId.equals(id) || !source.kind.equals("PAYMENT"))
          throw new Problem(400, "INVALID_SOURCE");
        var reversed =
            rows.stream()
                .filter(x -> Objects.equals(x.sourceId, source.id))
                .map(x -> x.amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (reversed.add(e.amount).compareTo(source.amount) > 0 || e.amount.compareTo(i.paid) > 0)
          throw new Problem(409, "OVER_REVERSAL");
        e.kind = "REVERSAL";
        e.sourceId = source.id;
        e.method = source.method;
        e.note = AdminService.text((String) v.get("note"), 800);
        i.paid = i.paid.subtract(e.amount);
      } else throw new Problem(404, "NOT_FOUND");
      db.save(e);
      i.status = i.paid.compareTo(i.total) == 0 ? "PAID" : i.paid.signum() > 0 ? "PARTIAL" : "OPEN";
    }
    i.revision++;
    access.audit("INVOICE_" + action, id, i.departmentId);
    return i;
  }

  private void internal() {
    if (!scope.internal()) throw new Problem(403, "OUT_OF_SCOPE");
  }

  private Set<Long> ids(Map<String, Object> v, String key) {
    if (!(v.get(key) instanceof List<?> rows) || rows.isEmpty() || rows.size() > 200)
      throw new Problem(400, "INVALID_SELECTION");
    var result = new LinkedHashSet<Long>();
    for (var row : rows) {
      Long id = FreightPolicy.id(Map.of("id", row), "id");
      if (!result.add(id)) throw new Problem(400, "DUPLICATE_SELECTION");
    }
    return result;
  }
}
