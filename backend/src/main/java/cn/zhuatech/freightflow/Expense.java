// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.freightflow;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;

/** 实际趟次报销费用与独立复核。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "expense")
public class Expense {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "trip_id", nullable = false)
  public Long tripId = null;

  @Column(name = "type", nullable = false, length = 30)
  public String type = "";

  @Column(name = "amount", nullable = false, precision = 18, scale = 2)
  public BigDecimal amount = BigDecimal.ZERO;

  @Column(name = "reference", nullable = false, length = 120)
  public String reference = "";

  @Column(name = "note", nullable = false, length = 800)
  public String note = "";

  @Column(name = "status", nullable = false, length = 30)
  public String status = "SUBMITTED";

  @Column(name = "created_by", nullable = false, length = 60)
  public String createdBy = "";

  @Column(name = "reviewed_by", nullable = false, length = 60)
  public String reviewedBy = "";

  @Column(name = "review_note", nullable = false, length = 800)
  public String reviewNote = "";

  @Column(name = "created_at", nullable = false)
  public Instant createdAt = null;

  @Column(name = "department_id", nullable = false)
  public Long departmentId = null;

  @Column(name = "revision", nullable = false)
  public int revision = 0;
}
