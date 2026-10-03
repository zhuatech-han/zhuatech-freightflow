// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.freightflow;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;

/** 派车、司机执行和运费约定快照。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "trip")
public class Trip {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "code", nullable = false, length = 60)
  public String code = "";

  @Column(name = "carrier_id", nullable = false)
  public Long carrierId = null;

  @Column(name = "carrier_name", nullable = false, length = 120)
  public String carrierName = "";

  @Column(name = "driver_id", nullable = false)
  public Long driverId = null;

  @Column(name = "driver_name", nullable = false, length = 120)
  public String driverName = "";

  @Column(name = "vehicle_id", nullable = false)
  public Long vehicleId = null;

  @Column(name = "vehicle_name", nullable = false, length = 120)
  public String vehicleName = "";

  @Column(name = "planned_start", nullable = false)
  public Instant plannedStart = null;

  @Column(name = "planned_end", nullable = false)
  public Instant plannedEnd = null;

  @Column(name = "started_at", nullable = true)
  public Instant startedAt = null;

  @Column(name = "closed_at", nullable = true)
  public Instant closedAt = null;

  @Column(name = "carrier_fee", nullable = false, precision = 18, scale = 2)
  public BigDecimal carrierFee = BigDecimal.ZERO;

  @Column(name = "invoice_id", nullable = true)
  public Long invoiceId = null;

  @Column(name = "status", nullable = false, length = 30)
  public String status = "DRAFT";

  @Column(name = "note", nullable = false, length = 800)
  public String note = "";

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
