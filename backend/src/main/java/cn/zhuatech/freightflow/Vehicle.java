// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.freightflow;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;

/** 车辆与重量、体积和件数容量。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "vehicle")
public class Vehicle {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "code", nullable = false, length = 60)
  public String code = "";

  @Column(name = "name", nullable = false, length = 120)
  public String name = "";

  @Column(name = "carrier_id", nullable = false)
  public Long carrierId = null;

  @Column(name = "max_weight_kg", nullable = false, precision = 18, scale = 3)
  public BigDecimal maxWeightKg = BigDecimal.ZERO;

  @Column(name = "max_volume_m3", nullable = false, precision = 18, scale = 3)
  public BigDecimal maxVolumeM3 = BigDecimal.ZERO;

  @Column(name = "max_pieces", nullable = false)
  public int maxPieces = 0;

  @Column(name = "department_id", nullable = false)
  public Long departmentId = null;

  @Column(name = "enabled", nullable = false)
  public boolean enabled = true;
}
