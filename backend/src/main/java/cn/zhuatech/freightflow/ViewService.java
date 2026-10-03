// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.freightflow;

import java.math.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/** 按岗位返回业务字段和有界列表；客户不读取拼车数据，司机不读取客户运价或财务流水。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Service
@Transactional(readOnly = true)
public class ViewService {
  final Store db;
  final AccessService auth;
  final FreightAccess access;
  final TransitService transit;
  final AdminService admin;
  final ProofService proofs;
  final JsonMapper json = JsonMapper.builder().build();

  public ViewService(
      Store db,
      AccessService auth,
      FreightAccess access,
      TransitService transit,
      AdminService admin,
      ProofService proofs) {
    this.db = db;
    this.auth = auth;
    this.access = access;
    this.transit = transit;
    this.admin = admin;
    this.proofs = proofs;
  }

  /** 岗位菜单与业务身份链接，展示基于服务端范围。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Map<String, Object> profile() {
    var m = new LinkedHashMap<>(auth.profile());
    m.put("customerId", access.customer());
    m.put("driverId", access.driver());
    m.put("internal", access.internal());
    return m;
  }

  /** 下拉选项只给授权内部岗位；对外只给企业配置与本人身份。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Map<String, Object> catalog() {
    var m = new LinkedHashMap<String, Object>();
    m.put("settings", db.all(SystemSetting.class));
    m.put("profile", profile());
    m.put(
        "methods",
        db.query(
            DictionaryEntry.class,
            "from DictionaryEntry where type='payment_method' and enabled=true"));
    if (auth.role().permissions.contains("master.read") && access.internal()) {
      m.put(
          "customers",
          db.all(Customer.class).stream().filter(x -> auth.visible(x.departmentId)).toList());
      m.put(
          "carriers",
          db.all(Carrier.class).stream().filter(x -> auth.visible(x.departmentId)).toList());
      m.put(
          "drivers",
          db.all(Driver.class).stream().filter(x -> auth.visible(x.departmentId)).toList());
      m.put(
          "vehicles",
          db.all(Vehicle.class).stream().filter(x -> auth.visible(x.departmentId)).toList());
    }
    if (auth.role().permissions.contains("admin")) {
      m.put("users", db.all(Account.class));
      m.put("roles", db.all(AccessRole.class));
      m.put("permissions", db.all(Permission.class));
      m.put("departments", db.all(Department.class));
    }
    return m;
  }

  /** 不允许客户端选择任意实体类型或排序表达式；最多100条一页。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Map<String, Object> list(
      String type, String q, String status, int page, int size, String sort, boolean descending) {
    if (q.length() > 120
        || page < 0
        || size < 1
        || size > 100
        || !Set.of("id", "code", "name", "status", "createdAt", "updatedAt", "total")
            .contains(sort)) throw new Problem(400, "INVALID_QUERY");
    List<Map<String, Object>> rows =
        switch (type) {
          case "shipments" -> {
            auth.require("shipment.read");
            yield db.all(Shipment.class).stream()
                .filter(access::shipment)
                .map(this::shipment)
                .toList();
          }
          case "trips" -> {
            auth.require("trip.read");
            yield db.all(Trip.class).stream().filter(access::trip).map(this::trip).toList();
          }
          case "expenses" -> {
            if (!auth.role().permissions.contains("expense.write")) auth.require("finance");
            yield db.all(Expense.class).stream()
                .filter(
                    x ->
                        auth.visible(x.departmentId)
                            && (access.internal()
                                || x.createdBy.equals(auth.current().username)
                                    && access.trip(db.get(Trip.class, x.tripId))))
                .map(this::map)
                .toList();
          }
          case "invoices" -> {
            auth.require("invoice.read");
            yield db.all(Invoice.class).stream().filter(access::invoice).map(this::map).toList();
          }
          case "audit" -> {
            auth.require("audit");
            yield db.all(AuditEvent.class).stream()
                .filter(x -> auth.visible(x.departmentId))
                .map(this::map)
                .toList();
          }
          case "customers", "carriers", "drivers", "vehicles" -> {
            auth.require("master.read");
            if (!access.internal()) throw new Problem(403, "OUT_OF_SCOPE");
            Class<?> cls =
                switch (type) {
                  case "customers" -> Customer.class;
                  case "carriers" -> Carrier.class;
                  case "drivers" -> Driver.class;
                  default -> Vehicle.class;
                };
            yield db.all(cls).stream()
                .map(this::map)
                .filter(x -> auth.visible(((Number) x.get("departmentId")).longValue()))
                .toList();
          }
          default -> admin.list(type).stream().map(this::map).toList();
        };
    String term = q.toLowerCase(Locale.ROOT);
    var filtered =
        new ArrayList<>(
            rows.stream()
                .filter(
                    x ->
                        (status.isBlank() || status.equals(String.valueOf(x.get("status"))))
                            && (q.isBlank()
                                || x.values().stream()
                                    .filter(Objects::nonNull)
                                    .anyMatch(
                                        v ->
                                            String.valueOf(v)
                                                .toLowerCase(Locale.ROOT)
                                                .contains(term))))
                .toList());
    Comparator<Map<String, Object>> comparator = (a, b) -> compare(a.get(sort), b.get(sort));
    if (descending) comparator = comparator.reversed();
    filtered.sort(comparator.thenComparing(x -> ((Number) x.get("id")).longValue()));
    int start = (int) Math.min((long) page * size, filtered.size());
    return Map.of(
        "items",
        filtered.subList(start, Math.min(start + size, filtered.size())),
        "total",
        filtered.size());
  }

  /** 运单详情包含自己货物事件、受控回单及账单，不返回同车其他货物。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Map<String, Object> shipmentDetail(Long id) {
    var s = db.get(Shipment.class, id);
    access.shipmentRead(s);
    return Map.of(
        "shipment",
        shipment(s),
        "events",
        db.query(TransportEvent.class, "from TransportEvent where shipmentId=?1", id),
        "proofs",
        db.query(DeliveryProof.class, "from DeliveryProof where shipmentId=?1", id).stream()
            .map(proofs::metadata)
            .toList());
  }

  /** 行程装载清单和事件，对司机移除运价、承运费用和账单信息。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Map<String, Object> tripDetail(Long id) {
    auth.require("trip.read");
    var t = db.get(Trip.class, id);
    if (!access.trip(t)) throw new Problem(403, "OUT_OF_SCOPE");
    var events =
        db.query(TransportEvent.class, "from TransportEvent where tripId=?1", id).stream()
            .filter(e -> !e.kind.startsWith("EXPENSE") || access.internal())
            .toList();
    return Map.of(
        "trip",
        trip(t),
        "shipments",
        transit.shipments(id).stream().map(this::shipment).toList(),
        "events",
        events);
  }

  /** 客户只能查看自己的应收对账明细，不能看承运付款单。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Map<String, Object> invoiceDetail(Long id) {
    auth.require("invoice.read");
    var i = db.get(Invoice.class, id);
    if (!access.invoice(i)) throw new Problem(403, "OUT_OF_SCOPE");
    return Map.of(
        "invoice",
        i,
        "lines",
        db.query(InvoiceLine.class, "from InvoiceLine where invoiceId=?1", id),
        "entries",
        db.query(SettlementEntry.class, "from SettlementEntry where invoiceId=?1", id));
  }

  /** 工作台统计来自当前权限下真实记录，无静态业绩。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Map<String, Object> dashboard() {
    auth.require("dashboard");
    var ss = db.all(Shipment.class).stream().filter(access::shipment).toList();
    var trips = db.all(Trip.class).stream().filter(access::trip).toList();
    var counts = new TreeMap<String, Long>();
    for (var s : ss) counts.merge(s.status, 1L, Long::sum);
    return Map.of(
        "counts",
        counts,
        "shipments",
        ss.stream()
            .sorted(Comparator.comparing((Shipment s) -> s.updatedAt).reversed())
            .limit(8)
            .map(this::shipment)
            .toList(),
        "trips",
        trips.stream()
            .filter(t -> !Set.of("CLOSED", "CANCELLED").contains(t.status))
            .limit(8)
            .map(this::trip)
            .toList(),
        "openIssues",
        db.all(TransportEvent.class).stream()
            .filter(
                e ->
                    e.status.equals("OPEN")
                        && e.shipmentId != null
                        && access.shipment(db.get(Shipment.class, e.shipmentId)))
            .count());
  }

  /** 期间签收和闭单利润与实际现金分开；未付应收/应付为全时点余额。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Map<String, Object> report(String from, String to) {
    auth.require("report");
    if (!access.internal()) throw new Problem(403, "OUT_OF_SCOPE");
    var a = LocalDate.parse(from);
    var b = LocalDate.parse(to);
    if (b.isBefore(a) || a.plusDays(366).isBefore(b)) throw new Problem(400, "INVALID_DATE_RANGE");
    Instant start = a.atStartOfDay(transit.zone()).toInstant(),
        end = b.plusDays(1).atStartOfDay(transit.zone()).toInstant();
    var delivered =
        db.all(Shipment.class).stream()
            .filter(
                s ->
                    auth.visible(s.departmentId)
                        && s.status.equals("DELIVERED")
                        && within(s.deliveredAt, start, end))
            .toList();
    BigDecimal received = BigDecimal.ZERO,
        paid = BigDecimal.ZERO,
        revenue = BigDecimal.ZERO,
        cost = BigDecimal.ZERO;
    for (var e : db.all(SettlementEntry.class)) {
      if (!auth.visible(e.departmentId) || !within(e.createdAt, start, end)) continue;
      var amount = e.kind.equals("REVERSAL") ? e.amount.negate() : e.amount;
      if (db.get(Invoice.class, e.invoiceId).kind.equals("RECEIVABLE"))
        received = received.add(amount);
      else paid = paid.add(amount);
    }
    var tripRows = new ArrayList<Map<String, Object>>();
    for (var t : db.all(Trip.class)) {
      if (!auth.visible(t.departmentId)
          || !t.status.equals("CLOSED")
          || !within(t.closedAt, start, end)) continue;
      BigDecimal
          sales =
              transit.shipments(t.id).stream()
                  .map(s -> s.freight)
                  .reduce(BigDecimal.ZERO, BigDecimal::add),
          expenses =
              db
                  .query(Expense.class, "from Expense where tripId=?1 and status='APPROVED'", t.id)
                  .stream()
                  .map(e -> e.amount)
                  .reduce(BigDecimal.ZERO, BigDecimal::add);
      var expense = t.carrierFee.add(expenses);
      revenue = revenue.add(sales);
      cost = cost.add(expense);
      tripRows.add(
          Map.of(
              "id",
              t.id,
              "code",
              t.code,
              "revenue",
              sales,
              "cost",
              expense,
              "profit",
              sales.subtract(expense)));
    }
    var invoices =
        db.all(Invoice.class).stream()
            .filter(i -> auth.visible(i.departmentId) && !i.status.equals("VOID"))
            .toList();
    BigDecimal
        ar =
            invoices.stream()
                .filter(i -> i.kind.equals("RECEIVABLE"))
                .map(i -> i.total.subtract(i.paid))
                .reduce(BigDecimal.ZERO, BigDecimal::add),
        ap =
            invoices.stream()
                .filter(i -> i.kind.equals("PAYABLE"))
                .map(i -> i.total.subtract(i.paid))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    return Map.of(
        "deliveredCount",
        delivered.size(),
        "closedTrips",
        tripRows,
        "revenue",
        revenue,
        "cost",
        cost,
        "profit",
        revenue.subtract(cost),
        "receipts",
        received,
        "disbursements",
        paid,
        "netCash",
        received.subtract(paid),
        "receivables",
        ar,
        "payables",
        ap);
  }

  /** 可复制业务对象转为字段白名单基础映射；司机财务字段另行移除。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @SuppressWarnings("unchecked")
  public Map<String, Object> map(Object x) {
    return json.convertValue(x, LinkedHashMap.class);
  }

  /** 运单财务字段只供内部和托运本人。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Map<String, Object> shipment(Shipment x) {
    var m = map(x);
    if (!access.internal() && access.driver() != null) {
      m.remove("freight");
      m.remove("invoiceId");
    }
    return m;
  }

  /** 行程财务字段不进入司机响应。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Map<String, Object> trip(Trip x) {
    var m = map(x);
    if (!access.internal()) {
      m.remove("carrierFee");
      m.remove("invoiceId");
    }
    return m;
  }

  private boolean within(Instant x, Instant a, Instant b) {
    return x != null && !x.isBefore(a) && x.isBefore(b);
  }

  private int compare(Object a, Object b) {
    if (a == null || b == null) return a == b ? 0 : a == null ? -1 : 1;
    if (a instanceof Number && b instanceof Number)
      return new BigDecimal(a.toString()).compareTo(new BigDecimal(b.toString()));
    return a.toString().compareToIgnoreCase(b.toString());
  }
}
