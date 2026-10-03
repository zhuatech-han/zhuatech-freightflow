// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.freightflow;

import jakarta.persistence.*;
import java.time.*;

/** 承运商和结算对象。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "carrier")
public class Carrier {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "code", nullable = false, length = 60)
  public String code = "";

  @Column(name = "name", nullable = false, length = 120)
  public String name = "";

  @Column(name = "contact", nullable = false, length = 120)
  public String contact = "";

  @Column(name = "phone", nullable = false, length = 60)
  public String phone = "";

  @Column(name = "department_id", nullable = false)
  public Long departmentId = null;

  @Column(name = "enabled", nullable = false)
  public boolean enabled = true;
}
