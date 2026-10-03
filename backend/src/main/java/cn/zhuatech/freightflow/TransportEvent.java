// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.freightflow;

import jakarta.persistence.*;
import java.time.*;

/** 运单与行程操作、异常及回单审核历史。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "transport_event")
public class TransportEvent {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "shipment_id", nullable = true)
  public Long shipmentId = null;

  @Column(name = "trip_id", nullable = true)
  public Long tripId = null;

  @Column(name = "kind", nullable = false, length = 60)
  public String kind = "";

  @Column(name = "note", nullable = false, length = 800)
  public String note = "";

  @Column(name = "status", nullable = false, length = 30)
  public String status = "RECORDED";

  @Column(name = "resolved_by", nullable = false, length = 60)
  public String resolvedBy = "";

  @Column(name = "resolution", nullable = false, length = 800)
  public String resolution = "";

  @Column(name = "actor", nullable = false, length = 60)
  public String actor = "";

  @Column(name = "created_at", nullable = false)
  public Instant createdAt = null;

  @Column(name = "department_id", nullable = false)
  public Long departmentId = null;

  @Column(name = "revision", nullable = false)
  public int revision = 0;
}
