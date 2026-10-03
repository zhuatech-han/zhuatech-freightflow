// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.freightflow;

import jakarta.persistence.*;
import java.time.*;

/** 受权限保护的原始签收图片与摘要。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "delivery_proof")
public class DeliveryProof {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "shipment_id", nullable = false)
  public Long shipmentId = null;

  @Column(name = "sha256", nullable = false, length = 64)
  public String sha256 = "";

  @Column(name = "mime", nullable = false, length = 30)
  public String mime = "";

  @Column(name = "size", nullable = false)
  public int size = 0;

  @Lob
  @com.fasterxml.jackson.annotation.JsonIgnore
  @Column(name = "payload", nullable = false, columnDefinition = "LONGBLOB")
  public byte[] payload = null;

  @Column(name = "actor", nullable = false, length = 60)
  public String actor = "";

  @Column(name = "created_at", nullable = false)
  public Instant createdAt = null;

  @Column(name = "department_id", nullable = false)
  public Long departmentId = null;
}
