// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.freightflow;

import jakarta.persistence.*;
import java.time.*;

/** 司机身份及证件有效日期。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "driver")
public class Driver {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "code", nullable = false, length = 60)
  public String code = "";

  @Column(name = "name", nullable = false, length = 120)
  public String name = "";

  @Column(name = "account_id", nullable = false)
  public Long accountId = null;

  @Column(name = "carrier_id", nullable = false)
  public Long carrierId = null;

  @Column(name = "phone", nullable = false, length = 60)
  public String phone = "";

  @Column(name = "license_number", nullable = false, length = 80)
  public String licenseNumber = "";

  @Column(name = "license_expires", nullable = true)
  public LocalDate licenseExpires = null;

  @Column(name = "department_id", nullable = false)
  public Long departmentId = null;

  @Column(name = "enabled", nullable = false)
  public boolean enabled = true;
}
