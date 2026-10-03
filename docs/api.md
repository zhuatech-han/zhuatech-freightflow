# 接口、权限与指令

知华科技（上海如静知华信息科技有限公司） · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2。

基地址同源 `/api`。先GET `/auth/csrf`取得`header/token`，POST `/auth/login`用户名密码后保存HttpOnly Cookie并重新获取CSRF。任何写请求需该令牌，所有业务需登录。GET `/auth/me`、POST `/auth/logout`、POST `/auth/password`（oldPassword/newPassword）。GET `/catalog`返回真实岗位菜单、权限、部门和允许的选项。

| 路径 | 行为 |
| --- | --- |
| GET `/dashboard` | 当前范围统计 |
| GET `/lists/{type}` | page从0开始，size1–100，q≤120，status、sort、descending；返回items/total |
| POST `/shipments`，PUT `/shipments/{id}` | 建草稿/编辑草稿；客户身份由服务端绑定 |
| POST `/shipments/import` | items数组1–200条，整批事务 |
| GET `/shipments/{id}` | shipment/events/proofs，按本人或实际司机隔离 |
| POST `/shipments/{id}/{action}` | quote/cancel/deliver/confirm-delivery/reject-delivery/issue |
| POST `/shipments/{id}/proofs` | multipart `file`，只允许实际司机，PNG/JPEG；返回元信息 |
| GET `/proofs/{id}` | 受控原始图像 |
| POST `/trips`，PUT `/trips/{id}` | 调度规划预约，返回车次 |
| GET `/trips/{id}` | trip/shipments/events；司机财务字段剔除 |
| POST `/trips/{id}/{action}` | add/remove/dispatch/accept/depart/close/cancel |
| POST `/issues/{id}/resolve` | 调度解决异常，说明必填 |
| POST `/expenses`，POST `/expenses/{id}/review` | 实报费用 / finance独立复核 |
| POST `/invoices`，GET `/invoices/{id}` | 冻结应收/应付，详情含invoice/lines/entries |
| POST `/invoices/{id}/{action}` | pay/reverse/void |
| GET `/reports?from=YYYY-MM-DD&to=YYYY-MM-DD`，`/reports.csv` | finance/report范围，最多366天跨度 |
| POST/PUT/DELETE `/master/{type}[/{id}]` | customers/carriers/drivers/vehicles，删除拒绝引用 |
| POST/PUT/DELETE `/admin/{type}[/{id}]` | users/roles/departments/dictionaries；权限、菜单、参数仅编辑已注册项 |

type白名单：shipments/trips/expenses/invoices/customers/carriers/drivers/vehicles/users/roles/departments/permissions/menus/dictionaries/settings/audit。排序只允许id/code/name/status/createdAt/updatedAt/total，不接受任意SQL。管理目录的创建删除按业务规则限制，接口不存在财务流水修改删除。

业务写入需`requestKey`（客户端生成的唯一字符串，最多80字节）与已有记录`revision`。相同账号、指令和相同正文重试只执行一次；更改内容须使用新键，旧修订号拒绝。不要将重试保护用于跨账号调用。

托运正文/导入数组行：

```json
{"customerId":1,"origin":"TEST pickup","destination":"TEST delivery","consignee":"TEST receiver","consigneePhone":"","goods":"TEST goods","pieces":2,"weightKg":"200.000","volumeM3":"2.000"}
```

数字ID由实际档案选择，示例不是预置客户。独立POST加requestKey；编辑再加revision。客户传customerId不改变自身关联。quote传freight精确两位或更少；deliver传receiver与receivedPieces全量件数，之前必须上传照片；cancel/reject-delivery/issue传note原因。

车次传carrierId/driverId/vehicleId、plannedStartLocal/plannedEndLocal（企业时区`YYYY-MM-DDTHH:mm`）或者plannedStart/plannedEnd（显式UTC分钟`...Z`）、carrierFee与note；同时包含Local字段时以Local输入为准。add传shipmentId；remove传shipmentId与note；cancel传note。accept/depart必须实际司机。

费用传tripId、type（FUEL/TOLL/PARKING/OTHER）、amount、reference、note，review传approved布尔与note。RECEIVABLE账单传customerId、shipmentIds唯一ID数组、dueDate；PAYABLE传tripId、dueDate。pay传amount/method/reference/note；reverse传sourceId/amount/reference/note；void传note。付款方式以启用的payment_method字典为准。

错误为HTTP 400校验、401未登录、403权限、404不存在、409状态/冲突以及413服务上传过大；上游网关可返回非JSON错误。正文包含code，常见STALE_VERSION、RETRY_CONTENT_CHANGED、INVALID_STATE、CAPACITY_EXCEEDED、RESOURCE_BUSY、PROOF_REQUIRED、INDEPENDENT_REVIEW_REQUIRED、OVERPAYMENT。账号认证失败统一错误，不暴露账号存在性。上传限制可能由框架先返回400或网关413。
