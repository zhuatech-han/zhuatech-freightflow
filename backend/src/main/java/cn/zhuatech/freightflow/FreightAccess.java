// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.freightflow;

import java.util.*;
import org.springframework.stereotype.Service;

/** 客户、司机与内部部门的行级权限；接口响应另做字段隔离。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Service
public class FreightAccess {
  final Store db;
  final AccessService access;

  public FreightAccess(Store db, AccessService access) {
    this.db = db;
    this.access = access;
  }

  /** 当前客户门户关联，未关联返回空而不是允许所有数据。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Long customer() {
    var x = db.query(Customer.class, "from Customer where accountId=?1", access.current().id);
    return x.isEmpty() ? null : x.getFirst().id;
  }

  /** 当前司机档案关联。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Long driver() {
    var x = db.query(Driver.class, "from Driver where accountId=?1", access.current().id);
    return x.isEmpty() ? null : x.getFirst().id;
  }

  /** 内部岗位判定；ASSIGNED始终需要具体业务关联。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public boolean internal() {
    return !access.role().scope.equals("ASSIGNED");
  }

  /** 运单客户本人或实际派工司机可见。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public boolean shipment(Shipment s) {
    if (!access.visible(s.departmentId)) return false;
    if (internal()) return true;
    if (Objects.equals(customer(), s.customerId)) return customer() != null;
    return s.tripId != null && trip(db.get(Trip.class, s.tripId));
  }

  /** 行程只允许部门内部或关联司机，客户不读取拼车其他客户数据。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public boolean trip(Trip t) {
    return access.visible(t.departmentId)
        && (internal() || driver() != null && Objects.equals(driver(), t.driverId));
  }

  /** 对账单按客户和部门隔离，司机无财务阅读权限。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public boolean invoice(Invoice i) {
    return access.visible(i.departmentId)
        && (internal()
            || i.kind.equals("RECEIVABLE")
                && customer() != null
                && Objects.equals(customer(), i.customerId));
  }

  /** 明确拒绝对象越权，不能仅以列表隐藏替代。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public void shipmentRead(Shipment s) {
    access.require("shipment.read");
    if (!shipment(s)) throw new Problem(403, "OUT_OF_SCOPE");
  }

  /** 实际司机才可以接单、发车与交回单，管理员不能冒充执行人。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public void execute(Trip t) {
    access.require("driver.execute");
    if (driver() == null || !Objects.equals(driver(), t.driverId) || !trip(t))
      throw new Problem(403, "NOT_ASSIGNED_DRIVER");
  }
}
