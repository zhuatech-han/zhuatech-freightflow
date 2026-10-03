// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.freightflow;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;

/** 不可变收付款和原流水冲正。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "settlement_entry")
public class SettlementEntry {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "invoice_id", nullable = false)
  public Long invoiceId = null;

  @Column(name = "source_id", nullable = true)
  public Long sourceId = null;

  @Column(name = "kind", nullable = false, length = 30)
  public String kind = "";

  @Column(name = "amount", nullable = false, precision = 18, scale = 2)
  public BigDecimal amount = BigDecimal.ZERO;

  @Column(name = "method", nullable = false, length = 30)
  public String method = "";

  @Column(name = "reference", nullable = false, length = 120)
  public String reference = "";

  @Column(name = "note", nullable = false, length = 800)
  public String note = "";

  @Column(name = "actor", nullable = false, length = 60)
  public String actor = "";

  @Column(name = "created_at", nullable = false)
  public Instant createdAt = null;

  @Column(name = "department_id", nullable = false)
  public Long departmentId = null;
}
