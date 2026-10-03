// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.freightflow;

import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 全新数据库初始化岗位、权限及私有管理员，不制造订单和收入。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Component
public class Bootstrap implements ApplicationRunner {
  final Store db;
  final BCryptPasswordEncoder encoder;

  @Value("${freightflow.admin-username}")
  String username;

  @Value("${freightflow.admin-password}")
  String password;

  @Value("${freightflow.seed-demo}")
  boolean demo;

  public Bootstrap(Store db, BCryptPasswordEncoder encoder) {
    this.db = db;
    this.encoder = encoder;
  }

  /** 一次性创建基础目录；已有库不重设密码和角色。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (!db.all(Account.class).isEmpty()) return;
    AdminService.validatePassword(password);
    if (!username.matches("[a-zA-Z0-9_.-]{3,60}"))
      throw new IllegalArgumentException("INVALID_ADMIN_USERNAME");
    var dep = new Department();
    dep.name = "主运营部 / Operations";
    db.save(dep);
    var permissions =
        Set.of(
            "dashboard",
            "shipment.read",
            "shipment.write",
            "dispatch",
            "trip.read",
            "driver.execute",
            "expense.write",
            "finance",
            "invoice.read",
            "master.read",
            "master.edit",
            "report",
            "admin",
            "audit");
    for (var code : new TreeSet<>(permissions)) {
      var p = new Permission();
      p.code = code;
      p.name =
          switch (code) {
            case "dashboard" -> "工作台 / Dashboard";
            case "shipment.read" -> "查看运单 / Read shipments";
            case "shipment.write" -> "录入运单 / Write shipments";
            case "dispatch" -> "调度与复核 / Dispatch & delivery review";
            case "trip.read" -> "查看车次 / Read trips";
            case "driver.execute" -> "司机执行 / Driver execution";
            case "expense.write" -> "填报费用 / Submit expenses";
            case "finance" -> "财务审核及结算 / Finance review & settlement";
            case "invoice.read" -> "查看对账单 / Read statements";
            case "master.read" -> "查看基础档案 / Read master data";
            case "master.edit" -> "管理基础档案 / Manage master data";
            case "report" -> "经营报表 / Business reports";
            case "admin" -> "账号及系统管理 / Administration";
            case "audit" -> "查看操作审计 / Read audit";
            default -> throw new IllegalStateException("Unknown registered permission");
          };
      db.save(p);
    }
    var admin = role("管理员 / Administrator", permissions, "ALL");
    role(
        "调度 / Dispatcher",
        Set.of(
            "dashboard",
            "shipment.read",
            "shipment.write",
            "dispatch",
            "trip.read",
            "expense.write",
            "master.read",
            "master.edit"),
        "DEPARTMENT");
    role(
        "财务 / Finance",
        Set.of(
            "dashboard",
            "shipment.read",
            "trip.read",
            "invoice.read",
            "finance",
            "master.read",
            "report"),
        "DEPARTMENT");
    role(
        "司机 / Driver",
        Set.of("dashboard", "shipment.read", "trip.read", "driver.execute", "expense.write"),
        "ASSIGNED");
    role(
        "客户 / Customer",
        Set.of("dashboard", "shipment.read", "shipment.write", "invoice.read"),
        "ASSIGNED");
    var a = new Account();
    a.username = username;
    a.displayName = "管理员 / Administrator";
    a.passwordHash = encoder.encode(password);
    a.roleId = admin.id;
    a.departmentId = dep.id;
    a.enabled = true;
    db.save(a);
    String[][] menus = {
      {"dashboard", "运营工作台", "Operations", "dashboard"},
      {"shipments", "运单", "Shipments", "shipment.read"},
      {"trips", "配载与派车", "Dispatch & trips", "trip.read"},
      {"expenses", "趟次费用", "Trip expenses", "trip.read"},
      {"invoices", "运费对账", "Freight settlement", "invoice.read"},
      {"reports", "经营报表", "Reports", "report"},
      {"customers", "客户", "Customers", "master.read"},
      {"carriers", "承运商", "Carriers", "master.read"},
      {"drivers", "司机", "Drivers", "master.read"},
      {"vehicles", "车辆", "Vehicles", "master.read"},
      {"users", "登录账号", "Accounts", "admin"},
      {"roles", "角色与权限", "Roles & permissions", "admin"},
      {"departments", "运营部门", "Departments", "admin"},
      {"menus", "导航菜单", "Navigation", "admin"},
      {"permissions", "权限目录", "Permissions", "admin"},
      {"dictionaries", "业务字典", "Dictionaries", "admin"},
      {"settings", "系统参数", "Settings", "admin"},
      {"audit", "操作审计", "Audit", "audit"}
    };
    for (int n = 0; n < menus.length; n++) {
      var m = new NavMenu();
      m.code = menus[n][0];
      m.name = menus[n][1];
      m.nameEn = menus[n][2];
      m.permissionCode = menus[n][3];
      m.position = n;
      m.enabled = true;
      db.save(m);
    }
    for (var e :
        Map.of(
                "companyName",
                "知华运输 / ZhuaTech FreightFlow",
                "currency",
                "CNY",
                "timezone",
                "Asia/Shanghai")
            .entrySet()) {
      var s = new SystemSetting();
      s.code = e.getKey();
      s.value = e.getValue();
      db.save(s);
    }
    for (var e :
        new String[][] {
          {"CASH", "现金", "Cash"},
          {"BANK", "银行转账", "Bank transfer"},
          {"OTHER", "其他已核实收付", "Other verified payment"}
        }) {
      var d = new DictionaryEntry();
      d.type = "payment_method";
      d.code = e[0];
      d.name = e[1];
      d.nameEn = e[2];
      d.enabled = true;
      db.save(d);
    }
    if (demo) {
      var c = new Carrier();
      c.code = "DEMO-CARRIER";
      c.name = "DEMO 示例承运商 / Fictional carrier";
      c.departmentId = dep.id;
      db.save(c);
      var x = new Customer();
      x.code = "DEMO-CUSTOMER";
      x.name = "DEMO 示例客户 / Fictional customer";
      x.departmentId = dep.id;
      db.save(x);
    }
  }

  private AccessRole role(String name, Set<String> permissions, String scope) {
    var r = new AccessRole();
    r.name = name;
    r.scope = scope;
    r.permissions = new HashSet<>(permissions);
    return db.save(r);
  }
}
