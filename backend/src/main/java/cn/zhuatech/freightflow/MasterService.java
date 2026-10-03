// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.freightflow;

import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 主数据真实增改删除与账号绑定保护；历史通过外键和快照保留。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Service
@Transactional
public class MasterService {
  final Store db;
  final AccessService access;
  final AdminService admin;

  public MasterService(Store db, AccessService access, AdminService admin) {
    this.db = db;
    this.access = access;
    this.admin = admin;
  }

  /** 白名单实体编辑，所有引用同部门；变更不得使派工容量或司机身份失效。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object save(String type, Long id, Map<String, Object> v) {
    access.require("master.edit");
    db.lock(Department.class, 1L);
    Long dep = FreightPolicy.id(v, "departmentId");
    access.department(dep);
    db.get(Department.class, dep);
    String code = AdminService.text((String) v.get("code"), 60),
        name = AdminService.text((String) v.get("name"), 120);
    boolean enabled = Boolean.TRUE.equals(v.get("enabled"));
    Object result;
    switch (type) {
      case "customers" -> {
        var x = id == null ? new Customer() : db.get(Customer.class, id);
        if (id != null) {
          access.department(x.departmentId);
          if (!x.departmentId.equals(dep)) throw new Problem(409, "IMMUTABLE_DEPARTMENT");
        }
        Long acct =
            v.get("accountId") == null || String.valueOf(v.get("accountId")).isBlank()
                ? null
                : FreightPolicy.id(v, "accountId");
        if (id != null
            && !Objects.equals(acct, x.accountId)
            && !db.query(Shipment.class, "from Shipment where customerId=?1", id).isEmpty())
          throw new Problem(409, "LINKED_ACCOUNT_IDENTITY");
        if (acct != null) {
          var a = db.get(Account.class, acct);
          same(a.departmentId, dep);
          if (!db.query(Driver.class, "from Driver where accountId=?1", acct).isEmpty())
            throw new Problem(409, "ACCOUNT_ALREADY_LINKED");
        }
        x.code = code;
        x.name = name;
        x.departmentId = dep;
        x.enabled = enabled;
        x.accountId = acct;
        x.contact = FreightPolicy.optional(v.get("contact"), 120);
        x.phone = FreightPolicy.optional(v.get("phone"), 60);
        x.address = FreightPolicy.optional(v.get("address"), 400);
        result = id == null ? db.save(x) : x;
        db.flush();
        if (acct != null) admin.validateLinkedRole(db.get(Account.class, acct));
      }
      case "carriers" -> {
        var x = id == null ? new Carrier() : db.get(Carrier.class, id);
        if (id != null) {
          access.department(x.departmentId);
          same(x.departmentId, dep);
        }
        x.code = code;
        x.name = name;
        x.departmentId = dep;
        x.enabled = enabled;
        x.contact = FreightPolicy.optional(v.get("contact"), 120);
        x.phone = FreightPolicy.optional(v.get("phone"), 60);
        result = id == null ? db.save(x) : x;
      }
      case "drivers" -> {
        var x = id == null ? new Driver() : db.get(Driver.class, id);
        var a = db.get(Account.class, FreightPolicy.id(v, "accountId"));
        var c = db.get(Carrier.class, FreightPolicy.id(v, "carrierId"));
        same(a.departmentId, dep);
        same(c.departmentId, dep);
        if (!db.query(Customer.class, "from Customer where accountId=?1", a.id).isEmpty())
          throw new Problem(409, "ACCOUNT_ALREADY_LINKED");
        if (id != null) {
          access.department(x.departmentId);
          same(x.departmentId, dep);
          if (!db.query(Trip.class, "from Trip where driverId=?1", id).isEmpty()
              && (!Objects.equals(a.id, x.accountId) || !Objects.equals(c.id, x.carrierId)))
            throw new Problem(409, "LINKED_ACCOUNT_IDENTITY");
          if (!enabled && active("driverId", id)) throw new Problem(409, "ACTIVE_ASSIGNMENT");
        }
        x.code = code;
        x.name = name;
        x.accountId = a.id;
        x.carrierId = c.id;
        x.departmentId = dep;
        x.enabled = enabled;
        x.phone = FreightPolicy.optional(v.get("phone"), 60);
        x.licenseNumber = FreightPolicy.optional(v.get("licenseNumber"), 80);
        x.licenseExpires =
            v.get("licenseExpires") == null || String.valueOf(v.get("licenseExpires")).isBlank()
                ? null
                : LocalDate.parse(String.valueOf(v.get("licenseExpires")));
        result = id == null ? db.save(x) : x;
        db.flush();
        admin.validateLinkedRole(a);
      }
      case "vehicles" -> {
        var x = id == null ? new Vehicle() : db.get(Vehicle.class, id);
        var c = db.get(Carrier.class, FreightPolicy.id(v, "carrierId"));
        same(c.departmentId, dep);
        if (id != null) {
          access.department(x.departmentId);
          same(x.departmentId, dep);
          if (active("vehicleId", id)) throw new Problem(409, "ACTIVE_ASSIGNMENT");
          if (!db.query(Trip.class, "from Trip where vehicleId=?1", id).isEmpty()
              && !Objects.equals(c.id, x.carrierId)) throw new Problem(409, "IMMUTABLE_CARRIER");
        }
        x.code = code;
        x.name = name;
        x.carrierId = c.id;
        x.maxWeightKg = FreightPolicy.decimal(v.get("maxWeightKg"), 3, true);
        x.maxVolumeM3 = FreightPolicy.decimal(v.get("maxVolumeM3"), 3, true);
        x.maxPieces = FreightPolicy.integer(v.get("maxPieces"), 1, 1000000);
        x.departmentId = dep;
        x.enabled = enabled;
        result = id == null ? db.save(x) : x;
      }
      default -> throw new Problem(404, "NOT_FOUND");
    }
    access.audit("MASTER_SAVE_" + type, id == null ? "NEW" : id, dep);
    return result;
  }

  /** 删除无业务引用的档案；引用由数据库拒绝。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public void delete(String type, Long id) {
    access.require("master.edit");
    db.lock(Department.class, 1L);
    Object x = db.get(type(type), id);
    Long dep =
        switch (x) {
          case Customer c -> c.departmentId;
          case Carrier c -> c.departmentId;
          case Driver c -> c.departmentId;
          case Vehicle c -> c.departmentId;
          default -> throw new Problem(404, "NOT_FOUND");
        };
    access.department(dep);
    db.delete(x);
    access.audit("MASTER_DELETE_" + type, id, dep);
  }

  /** 主数据类型仅来自路由白名单。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Class<?> type(String s) {
    return switch (s) {
      case "customers" -> Customer.class;
      case "carriers" -> Carrier.class;
      case "drivers" -> Driver.class;
      case "vehicles" -> Vehicle.class;
      default -> throw new Problem(404, "NOT_FOUND");
    };
  }

  private void same(Long a, Long b) {
    if (!Objects.equals(a, b)) throw new Problem(400, "DEPARTMENT_MISMATCH");
  }

  private boolean active(String field, Long id) {
    return db.all(Trip.class).stream()
        .anyMatch(
            t ->
                !Set.of("CLOSED", "CANCELLED").contains(t.status)
                    && (field.equals("driverId") ? t.driverId : t.vehicleId).equals(id));
  }
}
