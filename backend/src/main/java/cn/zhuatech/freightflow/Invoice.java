// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.freightflow;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;

/** 客户应收或承运应付对账单；不是税务发票。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "invoice")
public class Invoice {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "code", nullable = false, length = 60)
  public String code = "";

  @Column(name = "kind", nullable = false, length = 30)
  public String kind = "";

  @Column(name = "customer_id", nullable = true)
  public Long customerId = null;

  @Column(name = "carrier_id", nullable = true)
  public Long carrierId = null;

  @Column(name = "trip_id", nullable = true)
  public Long tripId = null;

  @Column(name = "party_name", nullable = false, length = 120)
  public String partyName = "";

  @Column(name = "total", nullable = false, precision = 18, scale = 2)
  public BigDecimal total = BigDecimal.ZERO;

  @Column(name = "paid", nullable = false, precision = 18, scale = 2)
  public BigDecimal paid = BigDecimal.ZERO;

  @Column(name = "due_date", nullable = false)
  public LocalDate dueDate = null;

  @Column(name = "status", nullable = false, length = 30)
  public String status = "OPEN";

  @Column(name = "department_id", nullable = false)
  public Long departmentId = null;

  @Column(name = "void_reason", nullable = false, length = 800)
  public String voidReason = "";

  @Column(name = "revision", nullable = false)
  public int revision = 0;

  @Column(name = "created_at", nullable = false)
  public Instant createdAt = null;

  @Column(name = "created_by", nullable = false, length = 60)
  public String createdBy = "";
}
