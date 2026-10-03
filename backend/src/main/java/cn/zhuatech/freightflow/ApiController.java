// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.freightflow;

import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/** 运输业务及管理HTTP入口；所有写入委托到带事务和权限校验的业务层。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@RestController
@RequestMapping("/api")
public class ApiController {
  final TransitService transit;
  final FinanceService finance;
  final ViewService view;
  final MasterService master;
  final AdminService admin;
  final ProofService proof;

  public ApiController(
      TransitService transit,
      FinanceService finance,
      ViewService view,
      MasterService master,
      AdminService admin,
      ProofService proof) {
    this.transit = transit;
    this.finance = finance;
    this.view = view;
    this.master = master;
    this.admin = admin;
    this.proof = proof;
  }

  /** 安全表单目录。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/catalog")
  public Object catalog() {
    return view.catalog();
  }

  /** 工作台真实权限统计。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/dashboard")
  public Object dashboard() {
    return view.dashboard();
  }

  /** 业务/管理分页列表及固定字段排序。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/lists/{type}")
  public Object lists(
      @PathVariable String type,
      @RequestParam(defaultValue = "") String q,
      @RequestParam(defaultValue = "") String status,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(defaultValue = "id") String sort,
      @RequestParam(defaultValue = "true") boolean descending) {
    return view.list(type, q, status, page, size, sort, descending);
  }

  /** 运单本人及实际司机详情。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/shipments/{id}")
  public Object shipment(@PathVariable Long id) {
    return view.shipmentDetail(id);
  }

  /** 创建或导入草稿。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/shipments")
  public Object createShipment(@RequestBody Map<String, Object> body) {
    return transit.createShipment(body);
  }

  /** 整批原子导入。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/shipments/import")
  public Object importShipments(@RequestBody Map<String, Object> body) {
    return transit.importShipments(body);
  }

  /** 修订号保护的草稿更新。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PutMapping("/shipments/{id}")
  public Object editShipment(@PathVariable Long id, @RequestBody Map<String, Object> body) {
    return transit.editShipment(id, body);
  }

  /** 运单状态指令。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/shipments/{id}/{action}")
  public Object shipmentAction(
      @PathVariable Long id, @PathVariable String action, @RequestBody Map<String, Object> body) {
    return switch (action) {
      case "deliver" -> view.shipment(transit.submitDelivery(id, body));
      case "issue" -> transit.issue(id, body);
      default -> transit.shipmentAction(id, action, body);
    };
  }

  /** 实际司机上传签收原始图片。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/shipments/{id}/proofs")
  public Object upload(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
    return proof.upload(id, file);
  }

  /** 登录后按运单权限获取原图，无静态公开链接。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/proofs/{id}")
  public ResponseEntity<byte[]> download(@PathVariable Long id) {
    var p = proof.get(id);
    return ResponseEntity.ok()
        .header("Cache-Control", "no-store")
        .header("Content-Security-Policy", "default-src 'none'; sandbox")
        .header("X-Content-Type-Options", "nosniff")
        .contentType(MediaType.parseMediaType(p.mime))
        .body(p.payload);
  }

  /** 授权行程详情和配载清单。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/trips/{id}")
  public Object trip(@PathVariable Long id) {
    return view.tripDetail(id);
  }

  /** 调度创建车辆和司机预约。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/trips")
  public Object createTrip(@RequestBody Map<String, Object> body) {
    return transit.saveTrip(null, body);
  }

  /** 派车草稿修改。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PutMapping("/trips/{id}")
  public Object editTrip(@PathVariable Long id, @RequestBody Map<String, Object> body) {
    return transit.saveTrip(id, body);
  }

  /** 派车状态指令；对司机响应移除财务字段。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/trips/{id}/{action}")
  public Object tripAction(
      @PathVariable Long id, @PathVariable String action, @RequestBody Map<String, Object> body) {
    return view.trip(transit.tripAction(id, action, body));
  }

  /** 调度异常解决记录。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/issues/{id}/resolve")
  public Object resolve(@PathVariable Long id, @RequestBody Map<String, Object> body) {
    return transit.resolve(id, body);
  }

  /** 实报趟次费用。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/expenses")
  public Object expense(@RequestBody Map<String, Object> body) {
    return finance.expense(body);
  }

  /** 独立费用复核。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/expenses/{id}/review")
  public Object reviewExpense(@PathVariable Long id, @RequestBody Map<String, Object> body) {
    return finance.reviewExpense(id, body);
  }

  /** 冻结客户/承运对账单。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/invoices")
  public Object invoice(@RequestBody Map<String, Object> body) {
    return finance.invoice(body);
  }

  /** 本人应收明细或内部财务详情。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/invoices/{id}")
  public Object invoiceDetail(@PathVariable Long id) {
    return view.invoiceDetail(id);
  }

  /** 收付款与原流水冲正。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/invoices/{id}/{action}")
  public Object invoiceAction(
      @PathVariable Long id, @PathVariable String action, @RequestBody Map<String, Object> body) {
    return finance.action(id, action, body);
  }

  /** 经营日区间报表。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/reports")
  public Object reports(@RequestParam String from, @RequestParam String to) {
    return view.report(from, to);
  }

  /** 报表业务CSV不插入品牌广告。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping(value = "/reports.csv", produces = "text/csv;charset=UTF-8")
  public ResponseEntity<String> csv(@RequestParam String from, @RequestParam String to) {
    var r = view.report(from, to);
    var out = new StringBuilder("\ufeffmetric,value\n");
    for (var e : r.entrySet())
      if (!e.getKey().equals("closedTrips"))
        out.append(FreightPolicy.csv(e.getKey()))
            .append(',')
            .append(FreightPolicy.csv(e.getValue()))
            .append('\n');
    return ResponseEntity.ok()
        .header("Content-Disposition", "attachment; filename=freight.csv")
        .body(out.toString());
  }

  /** 业务档案新增。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/master/{type}")
  public Object createMaster(@PathVariable String type, @RequestBody Map<String, Object> body) {
    return master.save(type, null, body);
  }

  /** 业务档案编辑。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PutMapping("/master/{type}/{id}")
  public Object updateMaster(
      @PathVariable String type, @PathVariable Long id, @RequestBody Map<String, Object> body) {
    return master.save(type, id, body);
  }

  /** 删除未引用档案。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @DeleteMapping("/master/{type}/{id}")
  public Object deleteMaster(@PathVariable String type, @PathVariable Long id) {
    master.delete(type, id);
    return Map.of("ok", true);
  }

  /** 管理目录新增。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/admin/{type}")
  public Object createAdmin(@PathVariable String type, @RequestBody AdminService.Input body) {
    return admin.save(type, null, body);
  }

  /** 管理目录更新。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PutMapping("/admin/{type}/{id}")
  public Object updateAdmin(
      @PathVariable String type, @PathVariable Long id, @RequestBody AdminService.Input body) {
    return admin.save(type, id, body);
  }

  /** 管理目录删除，保护最后管理员。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @DeleteMapping("/admin/{type}/{id}")
  public Object deleteAdmin(@PathVariable String type, @PathVariable Long id) {
    admin.delete(type, id);
    return Map.of("ok", true);
  }
}
