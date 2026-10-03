// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.freightflow;

import java.math.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 运单报价、配载派车、司机执行、签收复核和异常处理；事务先锁配置再加载业务记录。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Service
@Transactional
public class TransitService {
  final Store db;
  final AccessService access;
  final FreightAccess scope;
  final CommandService commands;
  final Clock clock;

  public TransitService(
      Store db, AccessService access, FreightAccess scope, CommandService commands, Clock clock) {
    this.db = db;
    this.access = access;
    this.scope = scope;
    this.commands = commands;
    this.clock = clock;
  }

  /** 当前UTC时间供有界业务与统计使用。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Instant now() {
    return clock.instant();
  }

  /** 门店/企业本地时区仅用于展示、证件日期和经营日切。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public ZoneId zone() {
    return ZoneId.of(
        db.query(SystemSetting.class, "from SystemSetting where code=?1", "timezone")
            .getFirst()
            .value);
  }

  /** 客户本人或内部录入草稿；价格只由调度核准。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Shipment createShipment(Map<String, Object> v) {
    access.require("shipment.write");
    db.lock(Department.class, 1L);
    var cmd = commands.open("SHIPMENT_CREATE", v);
    if (!cmd.fresh()) {
      var old = db.get(Shipment.class, cmd.stamp().resultId);
      scope.shipmentRead(old);
      return old;
    }
    var c =
        db.get(
            Customer.class, scope.internal() ? FreightPolicy.id(v, "customerId") : ownCustomer());
    access.department(c.departmentId);
    if (!c.enabled) throw new Problem(409, "DISABLED_CUSTOMER");
    var s = new Shipment();
    s.customerId = c.id;
    s.customerName = c.name;
    s.departmentId = c.departmentId;
    s.code = "W-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
    s.createdBy = access.current().username;
    s.createdAt = now();
    s.updatedAt = now();
    fill(s, v);
    db.save(s);
    cmd.stamp().resultId = s.id;
    event(s.id, null, "CREATED", "", s.departmentId);
    access.audit("SHIPMENT_CREATE", s.id, s.departmentId);
    return s;
  }

  /** 仅草稿允许编辑，修订号和命令指纹保护重试；客户不能改报价。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Shipment editShipment(Long id, Map<String, Object> v) {
    access.require("shipment.write");
    db.lock(Department.class, 1L);
    var s = db.get(Shipment.class, id);
    scope.shipmentRead(s);
    if (!scope.internal() && !Objects.equals(scope.customer(), s.customerId))
      throw new Problem(403, "OUT_OF_SCOPE");
    var cmd = commands.open("SHIPMENT_EDIT_" + id, v);
    if (!cmd.fresh()) return s;
    rev(s.revision, v);
    state(s.status, "DRAFT");
    fill(s, v);
    touch(s);
    event(id, null, "EDITED", "", s.departmentId);
    return s;
  }

  /** 批量JSON导入为一个事务，任一行错误整批回滚。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public List<Shipment> importShipments(Map<String, Object> v) {
    access.require("shipment.write");
    db.lock(Department.class, 1L);
    if (!(v.get("items") instanceof List<?> list) || list.isEmpty() || list.size() > 200)
      throw new Problem(400, "INVALID_IMPORT");
    var outer = commands.open("SHIPMENT_IMPORT", v);
    String key = String.valueOf(v.get("requestKey"));
    var result = new ArrayList<Shipment>();
    for (int n = 0; n < list.size(); n++) {
      if (!(list.get(n) instanceof Map<?, ?> row)) throw new Problem(400, "INVALID_IMPORT");
      var input = new HashMap<String, Object>();
      for (var e : row.entrySet()) {
        if (!(e.getKey() instanceof String k)) throw new Problem(400, "INVALID_IMPORT");
        input.put(k, e.getValue());
      }
      input.put(
          "requestKey",
          UUID.nameUUIDFromBytes((key + ":" + n).getBytes(java.nio.charset.StandardCharsets.UTF_8))
              .toString());
      result.add(createShipment(input));
    }
    if (outer.fresh()) outer.stamp().resultId = result.getFirst().id;
    return result;
  }

  /** 发布报价、取消草稿或批准/退回签收回单，非法状态明确拒绝。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Shipment shipmentAction(Long id, String action, Map<String, Object> v) {
    db.lock(Department.class, 1L);
    var s = db.get(Shipment.class, id);
    scope.shipmentRead(s);
    if (action.equals("cancel")) {
      access.require("shipment.write");
      if (!scope.internal() && !Objects.equals(scope.customer(), s.customerId))
        throw new Problem(403, "OUT_OF_SCOPE");
    } else {
      access.require("dispatch");
      internal();
    }
    var cmd = commands.open("SHIPMENT_" + id + "_" + action, v);
    if (!cmd.fresh()) return s;
    rev(s.revision, v);
    String note = FreightPolicy.optional(v.get("note"), 800);
    switch (action) {
      case "quote" -> {
        state(s.status, "DRAFT");
        var c = db.get(Customer.class, s.customerId);
        if (!c.enabled) throw new Problem(409, "DISABLED_CUSTOMER");
        s.freight = FreightPolicy.decimal(v.get("freight"), 2, false);
        s.status = "READY";
      }
      case "cancel" -> {
        if (!Set.of("DRAFT", "READY").contains(s.status)) throw new Problem(409, "INVALID_STATE");
        note = requiredNote(v);
        s.status = "CANCELLED";
      }
      case "confirm-delivery" -> {
        state(s.status, "POD_PENDING");
        var t = db.get(Trip.class, s.tripId);
        access.department(t.departmentId);
        if (!db.query(
                TransportEvent.class,
                "from TransportEvent where shipmentId=?1 and status='OPEN'",
                id)
            .isEmpty()) throw new Problem(409, "UNRESOLVED_ISSUE");
        if (db.query(DeliveryProof.class, "from DeliveryProof where shipmentId=?1", id).isEmpty())
          throw new Problem(409, "PROOF_REQUIRED");
        s.status = "DELIVERED";
        s.deliveredAt = now();
        t.updatedAt = now();
        t.revision++;
      }
      case "reject-delivery" -> {
        state(s.status, "POD_PENDING");
        note = requiredNote(v);
        s.status = "IN_TRANSIT";
        s.receiver = "";
      }
      default -> throw new Problem(404, "NOT_FOUND");
    }
    touch(s);
    event(id, s.tripId, action.toUpperCase(), note, s.departmentId);
    access.audit("SHIPMENT_" + action, id, s.departmentId);
    return s;
  }

  /** 创建或修改派车草稿，同时预留车辆及司机；一运单一车次，不拆批。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Trip saveTrip(Long id, Map<String, Object> v) {
    access.require("dispatch");
    internal();
    db.lock(Department.class, 1L);
    Trip t = id == null ? new Trip() : db.get(Trip.class, id);
    if (id != null) access.department(t.departmentId);
    var cmd = commands.open("TRIP_SAVE_" + id, v);
    if (!cmd.fresh()) return db.get(Trip.class, cmd.stamp().resultId);
    if (id != null) {
      rev(t.revision, v);
      state(t.status, "DRAFT");
    }
    var driver = db.get(Driver.class, FreightPolicy.id(v, "driverId"));
    var vehicle = db.get(Vehicle.class, FreightPolicy.id(v, "vehicleId"));
    var carrier = db.get(Carrier.class, FreightPolicy.id(v, "carrierId"));
    Long dep = driver.departmentId;
    access.department(dep);
    if (!vehicle.departmentId.equals(dep)
        || !carrier.departmentId.equals(dep)
        || !driver.carrierId.equals(carrier.id)
        || !vehicle.carrierId.equals(carrier.id)
        || id != null && !t.departmentId.equals(dep)) throw new Problem(400, "DEPARTMENT_MISMATCH");
    t.driverId = driver.id;
    t.driverName = driver.name;
    t.vehicleId = vehicle.id;
    t.vehicleName = vehicle.name;
    t.carrierId = carrier.id;
    t.carrierName = carrier.name;
    t.departmentId = dep;
    t.plannedStart =
        v.containsKey("plannedStartLocal")
            ? FreightPolicy.localInput(v.get("plannedStartLocal"), zone())
            : FreightPolicy.instant(v.get("plannedStart"));
    t.plannedEnd =
        v.containsKey("plannedEndLocal")
            ? FreightPolicy.localInput(v.get("plannedEndLocal"), zone())
            : FreightPolicy.instant(v.get("plannedEnd"));
    if (!t.plannedEnd.isAfter(t.plannedStart)
        || Duration.between(t.plannedStart, t.plannedEnd).compareTo(Duration.ofDays(30)) > 0
        || t.plannedEnd.isBefore(now())
        || t.plannedStart.isAfter(now().plus(Duration.ofDays(90))))
      throw new Problem(400, "INVALID_TIME");
    t.carrierFee = FreightPolicy.decimal(v.get("carrierFee"), 2, false);
    t.note = FreightPolicy.optional(v.get("note"), 800);
    validateResources(t);
    capacity(t, null);
    if (id == null) {
      t.code = "T-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
      t.createdBy = access.current().username;
      t.createdAt = now();
      t.updatedAt = now();
      db.save(t);
    }
    t.revision++;
    t.updatedAt = now();
    cmd.stamp().resultId = t.id;
    event(null, t.id, "TRIP_SAVED", "", dep);
    access.audit("TRIP_SAVE", t.id, dep);
    return t;
  }

  /** 配载、派车、接单、发车、完成；按岗位和合法状态执行。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Trip tripAction(Long id, String action, Map<String, Object> v) {
    db.lock(Department.class, 1L);
    var t = db.get(Trip.class, id);
    if (Set.of("accept", "depart").contains(action)) scope.execute(t);
    else {
      access.require("dispatch");
      internal();
      access.department(t.departmentId);
    }
    var cmd = commands.open("TRIP_" + id + "_" + action, v);
    if (!cmd.fresh()) return t;
    rev(t.revision, v);
    String note = FreightPolicy.optional(v.get("note"), 800);
    switch (action) {
      case "add" -> {
        state(t.status, "DRAFT");
        var s = db.get(Shipment.class, FreightPolicy.id(v, "shipmentId"));
        access.department(s.departmentId);
        if (!s.departmentId.equals(t.departmentId)) throw new Problem(400, "DEPARTMENT_MISMATCH");
        state(s.status, "READY");
        if (s.tripId != null) throw new Problem(409, "ALREADY_ASSIGNED");
        capacity(t, s);
        s.tripId = t.id;
        s.status = "PLANNED";
        touch(s);
        event(s.id, id, "LOADED", "", t.departmentId);
      }
      case "remove" -> {
        state(t.status, "DRAFT");
        var s = db.get(Shipment.class, FreightPolicy.id(v, "shipmentId"));
        if (!Objects.equals(s.tripId, t.id)) throw new Problem(409, "NOT_ON_TRIP");
        s.tripId = null;
        s.status = "READY";
        touch(s);
        event(s.id, id, "UNLOADED", requiredNote(v), t.departmentId);
      }
      case "dispatch" -> {
        state(t.status, "DRAFT");
        if (shipments(id).isEmpty()) throw new Problem(409, "EMPTY_TRIP");
        validateResources(t);
        capacity(t, null);
        t.status = "DISPATCHED";
      }
      case "accept" -> {
        state(t.status, "DISPATCHED");
        t.status = "ACCEPTED";
      }
      case "depart" -> {
        state(t.status, "ACCEPTED");
        if (now().isBefore(t.plannedStart.minus(Duration.ofHours(12))))
          throw new Problem(409, "TOO_EARLY");
        validateResources(t);
        t.status = "IN_TRANSIT";
        t.startedAt = now();
        for (var s : shipments(id)) {
          s.status = "IN_TRANSIT";
          touch(s);
          event(s.id, id, "DEPARTED", "", s.departmentId);
        }
      }
      case "close" -> {
        state(t.status, "IN_TRANSIT");
        if (shipments(id).stream().anyMatch(s -> !s.status.equals("DELIVERED")))
          throw new Problem(409, "DELIVERY_INCOMPLETE");
        if (!db.query(
                TransportEvent.class, "from TransportEvent where tripId=?1 and status='OPEN'", id)
            .isEmpty()) throw new Problem(409, "UNRESOLVED_ISSUE");
        if (!db.query(Expense.class, "from Expense where tripId=?1 and status='SUBMITTED'", id)
            .isEmpty()) throw new Problem(409, "EXPENSE_UNREVIEWED");
        t.status = "CLOSED";
        t.closedAt = now();
      }
      case "cancel" -> {
        if (!Set.of("DRAFT", "DISPATCHED", "ACCEPTED").contains(t.status))
          throw new Problem(409, "INVALID_STATE");
        note = requiredNote(v);
        if (!db.query(Expense.class, "from Expense where tripId=?1", id).isEmpty())
          throw new Problem(409, "EXPENSE_EXISTS");
        for (var s : shipments(id)) {
          s.tripId = null;
          s.status = "READY";
          touch(s);
          event(s.id, id, "TRIP_CANCELLED", note, t.departmentId);
        }
        t.status = "CANCELLED";
      }
      default -> throw new Problem(404, "NOT_FOUND");
    }
    t.revision++;
    t.updatedAt = now();
    event(null, id, action.toUpperCase(), note, t.departmentId);
    access.audit("TRIP_" + action, id, t.departmentId);
    return t;
  }

  /** 司机提交全量签收，必须有原始照片；调度另行审核。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Shipment submitDelivery(Long id, Map<String, Object> v) {
    db.lock(Department.class, 1L);
    var s = db.get(Shipment.class, id);
    scope.shipmentRead(s);
    if (s.tripId == null) throw new Problem(409, "NOT_ON_TRIP");
    var t = db.get(Trip.class, s.tripId);
    scope.execute(t);
    var cmd = commands.open("DELIVER_" + id, v);
    if (!cmd.fresh()) return s;
    rev(s.revision, v);
    state(t.status, "IN_TRANSIT");
    state(s.status, "IN_TRANSIT");
    if (FreightPolicy.integer(v.get("receivedPieces"), 1, 1000000) != s.pieces)
      throw new Problem(409, "PARTIAL_DELIVERY_UNSUPPORTED");
    if (db.query(DeliveryProof.class, "from DeliveryProof where shipmentId=?1", id).isEmpty())
      throw new Problem(409, "PROOF_REQUIRED");
    s.receiver = AdminService.text((String) v.get("receiver"), 120);
    s.status = "POD_PENDING";
    touch(s);
    event(
        id, t.id, "DELIVERY_SUBMITTED", FreightPolicy.optional(v.get("note"), 800), s.departmentId);
    return s;
  }

  /** 运输异常不删除历史；解决必须由调度记录原因，并影响关单。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public TransportEvent issue(Long shipmentId, Map<String, Object> v) {
    db.lock(Department.class, 1L);
    var s = db.get(Shipment.class, shipmentId);
    scope.shipmentRead(s);
    if (s.tripId == null) throw new Problem(409, "NOT_ON_TRIP");
    var t = db.get(Trip.class, s.tripId);
    if (scope.internal()) {
      access.require("dispatch");
    } else scope.execute(t);
    if (!t.status.equals("IN_TRANSIT") || !Set.of("IN_TRANSIT", "POD_PENDING").contains(s.status))
      throw new Problem(409, "INVALID_STATE");
    var cmd = commands.open("ISSUE_" + shipmentId, v);
    if (!cmd.fresh()) return db.get(TransportEvent.class, cmd.stamp().resultId);
    var e = event(shipmentId, t.id, "ISSUE", requiredNote(v), s.departmentId);
    e.status = "OPEN";
    cmd.stamp().resultId = e.id;
    touch(s);
    return e;
  }

  /** 解决异常保留原报告，与审核分离。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public TransportEvent resolve(Long id, Map<String, Object> v) {
    access.require("dispatch");
    internal();
    db.lock(Department.class, 1L);
    var e = db.get(TransportEvent.class, id);
    access.department(e.departmentId);
    var cmd = commands.open("RESOLVE_" + id, v);
    if (!cmd.fresh()) return e;
    rev(e.revision, v);
    state(e.status, "OPEN");
    e.resolution = requiredNote(v);
    e.resolvedBy = access.current().username;
    e.status = "RESOLVED";
    e.revision++;
    event(e.shipmentId, e.tripId, "ISSUE_RESOLVED", e.resolution, e.departmentId);
    return e;
  }

  /** 货物容量三项同时守恒，不因体积或件数缺省绕过限载。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public void capacity(Trip t, Shipment extra) {
    var v = db.get(Vehicle.class, t.vehicleId);
    var all = new ArrayList<>(t.id == null ? List.<Shipment>of() : shipments(t.id));
    if (extra != null) all.add(extra);
    BigDecimal weight = BigDecimal.ZERO, volume = BigDecimal.ZERO;
    long pcs = 0;
    for (var s : all) {
      weight = weight.add(s.weightKg);
      volume = volume.add(s.volumeM3);
      pcs += s.pieces;
    }
    if (weight.compareTo(v.maxWeightKg) > 0
        || volume.compareTo(v.maxVolumeM3) > 0
        || pcs > v.maxPieces) throw new Problem(409, "CAPACITY_EXCEEDED");
  }

  /** 获取车次的全部运单，调用方须先验证权限。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public List<Shipment> shipments(Long trip) {
    return db.query(Shipment.class, "from Shipment where tripId=?1", trip);
  }

  /** 同事务追加状态/操作事件，不存秘密和广告。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public TransportEvent event(Long shipment, Long trip, String kind, String note, Long dep) {
    var e = new TransportEvent();
    e.shipmentId = shipment;
    e.tripId = trip;
    e.kind = kind;
    e.note = note;
    e.departmentId = dep;
    e.actor = access.current().username;
    e.createdAt = now();
    return db.save(e);
  }

  /** 修订号拦截过期页面，幂等重试由上层先判定。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public void rev(int actual, Map<String, Object> v) {
    if (actual != FreightPolicy.integer(v.get("revision"), 0, Integer.MAX_VALUE))
      throw new Problem(409, "STALE_VERSION");
  }

  private void validateResources(Trip t) {
    var driver = db.get(Driver.class, t.driverId);
    var vehicle = db.get(Vehicle.class, t.vehicleId);
    var carrier = db.get(Carrier.class, t.carrierId);
    var acct = db.get(Account.class, driver.accountId);
    if (!driver.enabled
        || !vehicle.enabled
        || !carrier.enabled
        || !acct.enabled
        || !db.get(AccessRole.class, acct.roleId).permissions.contains("driver.execute"))
      throw new Problem(409, "DISABLED_RESOURCE");
    if (driver.licenseExpires != null
        && driver.licenseExpires.isBefore(t.plannedEnd.atZone(zone()).toLocalDate()))
      throw new Problem(409, "LICENSE_EXPIRED");
    for (var x : db.all(Trip.class)) {
      if (Objects.equals(x.id, t.id)
          || Set.of("CLOSED", "CANCELLED").contains(x.status)
          || !x.driverId.equals(t.driverId) && !x.vehicleId.equals(t.vehicleId)) continue;
      if (x.status.equals("IN_TRANSIT")
          || FreightPolicy.overlap(t.plannedStart, t.plannedEnd, x.plannedStart, x.plannedEnd))
        throw new Problem(409, "RESOURCE_BUSY");
    }
  }

  private void fill(Shipment s, Map<String, Object> v) {
    s.origin = AdminService.text((String) v.get("origin"), 400);
    s.destination = AdminService.text((String) v.get("destination"), 400);
    s.consignee = AdminService.text((String) v.get("consignee"), 120);
    s.consigneePhone = FreightPolicy.optional(v.get("consigneePhone"), 60);
    s.goods = AdminService.text((String) v.get("goods"), 400);
    s.pieces = FreightPolicy.integer(v.get("pieces"), 1, 1000000);
    s.weightKg = FreightPolicy.decimal(v.get("weightKg"), 3, true);
    s.volumeM3 = FreightPolicy.decimal(v.get("volumeM3"), 3, true);
  }

  private void touch(Shipment s) {
    s.revision++;
    s.updatedAt = now();
  }

  private Long ownCustomer() {
    Long x = scope.customer();
    if (x == null) throw new Problem(403, "CUSTOMER_NOT_LINKED");
    return x;
  }

  private void internal() {
    if (!scope.internal()) throw new Problem(403, "OUT_OF_SCOPE");
  }

  private void state(String a, String b) {
    if (!a.equals(b)) throw new Problem(409, "INVALID_STATE");
  }

  private String requiredNote(Map<String, Object> v) {
    return AdminService.text((String) v.get("note"), 800);
  }
}
