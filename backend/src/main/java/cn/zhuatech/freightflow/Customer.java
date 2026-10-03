// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.freightflow;

import jakarta.persistence.*;
import java.time.*;

/** 托运客户与可选门户账号。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "customer")
public class Customer {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "code", nullable = false, length = 60)
  public String code = "";

  @Column(name = "name", nullable = false, length = 120)
  public String name = "";

  @Column(name = "account_id", nullable = true)
  public Long accountId = null;

  @Column(name = "contact", nullable = false, length = 120)
  public String contact = "";

  @Column(name = "phone", nullable = false, length = 60)
  public String phone = "";

  @Column(name = "address", nullable = false, length = 400)
  public String address = "";

  @Column(name = "department_id", nullable = false)
  public Long departmentId = null;

  @Column(name = "enabled", nullable = false)
  public boolean enabled = true;
}
