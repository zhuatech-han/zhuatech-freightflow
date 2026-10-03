// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.freightflow;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;

/** 对账单冻结明细。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "invoice_line")
public class InvoiceLine {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "invoice_id", nullable = false)
  public Long invoiceId = null;

  @Column(name = "shipment_id", nullable = true)
  public Long shipmentId = null;

  @Column(name = "description", nullable = false, length = 400)
  public String description = "";

  @Column(name = "amount", nullable = false, precision = 18, scale = 2)
  public BigDecimal amount = BigDecimal.ZERO;
}
