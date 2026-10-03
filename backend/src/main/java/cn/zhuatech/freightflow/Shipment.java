// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.freightflow;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;

/** 运单与货物、客户报价及签收快照。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "shipment")
public class Shipment {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "code", nullable = false, length = 60)
  public String code = "";

  @Column(name = "customer_id", nullable = false)
  public Long customerId = null;

  @Column(name = "customer_name", nullable = false, length = 120)
  public String customerName = "";

  @Column(name = "origin", nullable = false, length = 400)
  public String origin = "";

  @Column(name = "destination", nullable = false, length = 400)
  public String destination = "";

  @Column(name = "consignee", nullable = false, length = 120)
  public String consignee = "";

  @Column(name = "consignee_phone", nullable = false, length = 60)
  public String consigneePhone = "";

  @Column(name = "goods", nullable = false, length = 400)
  public String goods = "";

  @Column(name = "pieces", nullable = false)
  public int pieces = 0;

  @Column(name = "weight_kg", nullable = false, precision = 18, scale = 3)
  public BigDecimal weightKg = BigDecimal.ZERO;

  @Column(name = "volume_m3", nullable = false, precision = 18, scale = 3)
  public BigDecimal volumeM3 = BigDecimal.ZERO;

  @Column(name = "freight", nullable = false, precision = 18, scale = 2)
  public BigDecimal freight = BigDecimal.ZERO;

  @Column(name = "status", nullable = false, length = 30)
  public String status = "DRAFT";

  @Column(name = "trip_id", nullable = true)
  public Long tripId = null;

  @Column(name = "invoice_id", nullable = true)
  public Long invoiceId = null;

  @Column(name = "receiver", nullable = false, length = 120)
  public String receiver = "";

  @Column(name = "delivered_at", nullable = true)
  public Instant deliveredAt = null;

  @Column(name = "department_id", nullable = false)
  public Long departmentId = null;

  @Column(name = "revision", nullable = false)
  public int revision = 0;

  @Column(name = "created_by", nullable = false, length = 60)
  public String createdBy = "";

  @Column(name = "created_at", nullable = false)
  public Instant createdAt = null;

  @Column(name = "updated_at", nullable = false)
  public Instant updatedAt = null;
}
