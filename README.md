<div align="center">
<img src="frontend/public/brand/logo.jpg" width="56" alt="知华科技 LOGO">
<h1>知华运输与运费结算 · FreightFlow</h1>
<p>客户托运 → 调度配载 → 司机运输 → 回单复核 → 运费对账</p>
<p>知华科技（上海如静知华信息科技有限公司） · <a href="https://www.zhuatech.cn/">官方网站</a></p>
<p><b>1.0.0 · 公开源码学习版 / 非商业源码版</b></p>
</div>

A self-hosted road-freight workflow with Chinese / English UI, driver assignment, photo proof of delivery, independent reviews, receivable/payable statements and manual settlement ledgers. This is a **non-commercial source edition**, not an OSI-approved open-source licence. Commercial use requires written permission; see [LICENSE](LICENSE).

## 一票货、一趟车、两边账

适合需要从表格、纸质运单转到统一工作台的小型公路货运团队、托运协调人员，以及研究运输业务实现的开发者。系统将客户货物、车次执行、回单与应收应付关联起来，减少重复登记，让未签收、未审核费用、未收款分别可查。

| 操作岗位 | 实际可操作内容 |
| --- | --- |
| 客户门户 | 录入本人托运草稿，查看报价、运输状态、签收照片与本人应收对账单 |
| 调度工作台 | 审核报价，按重量、体积、件数配载，派车，处理异常，独立复核回单，关单 |
| 司机业务端 | 只看关联车次和货物；接单、发车、上传实际 PNG/JPEG 回单、填报全量签收及费用；手机浏览器可用 |
| 财务工作台 | 由不同账号核准费用；建立客户应收及承运应付，登记分次实际收付款、关联原流水冲正，查询利润与现金报表 |
| 管理后台 | 客户、承运商、司机、车辆、账号、角色、权限、菜单、部门、字典、参数与审计 |

客户运单：`草稿 → 待配载 → 已配载 → 运输中 → 回单待审 → 已签收`。车次：`草稿 → 已派车 → 司机接单 → 运输中 → 已关单`。接单、发车及交回单必须由实际关联司机执行，管理员不能代替司机。未发车车次可说明原因取消，并释放运单和资源；已发车车次不支持取消。

车次草稿即预留司机和车辆，计划区间按左闭右开判断冲突。正在实际运输中的资源保持占用，直至关单，不因计划结束时间过去而释放。重量、体积、件数任一超限均拒绝配载。运单未全部签收、存在未解决异常或未审核费用时拒绝关单。

客户账单只选已复核签收的运单；承运账单只选已关单车次，金额为约定承运费加核准报销费用。账单明细冻结，支付与冲正保留原始流水，不允许覆盖或删除。零金额账单直接结清，不制造付款流水。

**适用边界**：单企业、多个部门，单车次直运；一次全量签收，人工记录支付事实。没有 GPS 实时定位、线路优化、电子发票、支付网关、电子签章、保险理赔、跨境报关、部分签收、途中换车、多段转运或库存管理。照片回单不等于具有特定法律效力的电子签名。平台不代收款，不调用付费服务；外部接口对接需另行开发与配置。没有已验证客户数量或收益承诺。

## 实际运行页面

以下为隔离测试库实际运行截图，所有业务样例均以 `TEST` 标识，非真实客户或经营数据。

| 登录 | 客户端首页 |
| --- | --- |
| ![登录](docs/images/screenshots/login.png) | ![客户首页](docs/images/screenshots/customer-home.png) |

![调度运单与配载](docs/images/screenshots/dispatch.png)
![手机司机回单](docs/images/screenshots/driver-mobile.png)
![回单复核与运输记录](docs/images/screenshots/shipment.png)
![财务对账与原始流水](docs/images/screenshots/settlement.png)
![经营统计](docs/images/screenshots/reports.png)
![账号管理](docs/images/screenshots/accounts.png)
![角色与数据权限](docs/images/screenshots/permissions.png)
![系统参数](docs/images/screenshots/settings.png)
![English UI](docs/images/screenshots/english.png)

用户端与管理端共用登录入口，菜单及对象范围由数据库角色控制；不存在匿名自助注册或共享演示密码。详细流程见 [操作手册](docs/manual.md)。

## 从空库启动

环境：Docker Engine 与 Docker Compose v2；或者 Java 21、Maven 3.9、Node.js 24.19.0+、npm、MySQL 8.4。建议为完整构建预留 4 GB 内存。首次拉取镜像及依赖需要可访问相应软件仓库。

```sh
python3 scripts/init-env.py
# 查看本机私有 .env 中的 ADMIN_PASSWORD，妥善保管；不要粘贴进公开 Issue。
docker compose config --quiet
docker compose up --build -d --wait --wait-timeout 240
```

前端：http://127.0.0.1:8101/ 。健康检查：http://127.0.0.1:8101/actuator/health 。初始化管理员用户名默认 `admin`；密码由初始化脚本随机产生并写入权限为 0600 的 `.env`。脚本拒绝覆盖已有文件，无固定弱默认密码。系统首次启动创建管理员、五类角色、十四个权限、菜单、部门、支付方式字典及公司/币种/时区参数。已有库重启不重置账号密码。

`SEED_DEMO=true` 可在新库创建明确标记 `DEMO` 的客户与承运商基础档案，不创建司机、业务账号、运单、车次或收入。实际运输前，在后台创建客户/司机/调度/财务账号，绑定档案并录入车辆；步骤见操作手册。

| 环境变量 | 含义 |
| --- | --- |
| `MYSQL_ROOT_PASSWORD`、`DATABASE_PASSWORD` | 独立数据库强密码，无默认值 |
| `ADMIN_USERNAME`、`ADMIN_PASSWORD` | 首次初始化管理员；密码12–72位，含大小写字母和数字 |
| `WEB_PORT`、`BIND_ADDRESS` | 默认8101、127.0.0.1；端口冲突可覆盖，例如 `WEB_PORT=18103 docker compose up -d` |
| `COOKIE_SECURE` | 默认false适用于本机HTTP；HTTPS部署设true |
| `SEED_DEMO` | 默认false，仅新库可选基础演示档案 |
| `DATABASE_URL`、`DATABASE_USER` | 可选外部MySQL连接；URL为 `jdbc:mariadb://...`，外部网络须验证服务端证书 |

完整配置名见 [.env.example](.env.example)。默认 Compose 的数据库与后端不暴露主机端口，仅 Nginx 监听本机。HTTPS、域名、证书、备份存储与外部MySQL是运营方配置；生产部署须先取得商业授权。详见 [部署、升级与恢复](docs/deployment.md)。

本地开发：先准备全新 MySQL 数据库与低权限账号，按示例注入 `DATABASE_URL/DATABASE_USER/DATABASE_PASSWORD/ADMIN_PASSWORD`。运行 `mvn -f backend/pom.xml spring-boot:run`；另开终端 `cd frontend && npm ci && npm run dev`。Vite仅在开发模式代理到本机8080；生产前端使用同源 `/api`，不会写死本机后端地址。

## 工程与数据

```text
backend/        Java 21 / Spring Boot 4.0.7；认证、权限、运输、回单、财务、报表
frontend/       Vue 3.5 / Vite 8；中英文操作页面及移动适配
scripts/        私有环境初始化、真实HTTP验收、发布检查
compose.yaml    MySQL → 后端 → Nginx 的健康依赖与独立持久卷
docs/           操作、接口、架构、数据库、安全、部署及测试说明
```

后端使用 Spring Security、JPA、BCrypt(12)、会话与 CSRF；Flyway 管理数据库，MariaDB JDBC 驱动连接 MySQL。前端使用 Lucide 图标，ESLint / Prettier；Nginx同源代理与SPA回退。MySQL 8.4、版本化 SQL、外键、检查约束与索引保证真实持久化。金额精确到分，不自动舍入；数据库与时间戳使用UTC，计划时间按系统时区录入，夏令时缺失/歧义分钟会拒绝。

初始迁移：[V1__freight_schema.sql](backend/src/main/resources/db/migration/V1__freight_schema.sql)，包含22张应用表，Flyway另建历史表。照片原始二进制与校验值存入数据库，恢复备份时一并恢复，不依赖临时文件路径。币种与时区存在业务记录后锁定，避免重释历史金额或日期。数据库结构见 [数据库说明](docs/database.md)；接口见 [接口与状态约定](docs/api.md)；架构及一致性边界见 [架构](docs/architecture.md)。

升级前备份并验证恢复，以新增版本迁移文件升级，禁止改动已执行的V1、删除Flyway历史或执行repair来掩盖差异。JPA只验证结构，不自动建表。现有数据的回滚通过停机恢复已验证备份处理。

## 验证与排错

```sh
mvn -B -f backend/pom.xml spotless:check test package
cd frontend
npm ci --no-audit --no-fund
npm run format:check
npm run lint
npm test
npm run build
cd ..
python3 scripts/release-check.py
git diff --check
docker compose config --quiet
docker compose build
```

隔离的全新测试数据库启动后可执行：

```sh
python3 scripts/smoke.py --base http://127.0.0.1:8101 --env .env --allow-test-writes
```

验收脚本**写入带TEST标记的数据**，仅用于可丢弃数据库，不要用于已有业务库。后端覆盖状态、金额、权限、限载、重试、冲正与并发写入；真实MySQL及浏览器检查见 [测试说明](docs/testing.md)。Docker后端构建包含测试，不能跳过。

| 现象 | 处理 |
| --- | --- |
| Compose提示缺少配置 | 初始化私有 `.env`，确认三个密码变量非空 |
| 登录失败 | 用首次初始化密码；旧库不会因环境变量改变而重置账号；连续失败有短暂限流 |
| 不能操作司机车次 | 核对司机档案绑定账号、ASSIGNED范围及车次司机；管理员不能代接单 |
| 司机或车辆占用 | 查看未完成车次；取消尚未发车的预留或完成实际运输关单 |
| 回单不能提交/关单 | 上传有效PNG/JPEG，核对全量件数，解决异常，再由调度审核；费用须独立复核 |
| 数据库不健康 | 检查隔离项目日志与账号权限，保留数据库卷；不要删除旧卷“修复” |
| Flyway校验失败 | 恢复原迁移版本或按正式升级流程处理；不要篡改历史校验值 |
| Cookie未被浏览器保存 | 本机HTTP保持COOKIE_SECURE=false；HTTPS部署设true |

## 安全、贡献与使用责任

凭证只注入私有环境；禁止提交 `.env`、数据库备份、Cookie、客户数据或未脱敏日志。账号密码加密，会话 HttpOnly / SameSite=Strict；接口与行权限由服务端执行，角色变更及停用即时生效。上传最多2MiB/张、最多5张/运单，仅有效PNG/JPEG，限制像素并校验图片解码；下载按对象权限控制，无任意路径访问。参见 [安全说明](docs/security.md) 与 [第三方声明](docs/third-party.md)。

普通问题与改进建议可通过仓库 Issues 提交复现步骤；不要附真实客户资料或凭证。贡献前提交对应业务场景与验证结果，保留原版权；变更需通过测试、格式和构建检查。安全漏洞不要公开细节，使用下方官方微信私下反馈并说明影响及复现条件。

本学习版本按现状提供。运营方应评估业务适配、权限、部署、备份恢复及使用地区要求；系统报表为业务统计，不替代法定会计、税务或支付对账。具体商业授权范围以书面合同为准。

## 联系知华科技

本项目由知华科技（上海如静知华信息科技有限公司）提供公开源码学习版本，主要用于个人学习、技术研究与非商业交流。未经书面授权不得商用。企业信息化建设、中小企业数字化转型、中小企业 AI 转型、私有化部署、软件外包、软件项目外包、软件实施、FDE 外包、OPC 技术支持及深度定制开发，请访问知华科技官网 [https://www.zhuatech.cn/](https://www.zhuatech.cn/)，或添加微信 zhuatech、zhuatech2 咨询。

- 官网：https://www.zhuatech.cn/
- 商业授权、定制开发、部署、系统集成及源码授权微信：**zhuatech**、**zhuatech2**。
- 自有源码许可见 [LICENSE](LICENSE)；第三方组件保留其原许可证。

<table><tr><td align="center" width="260"><img src="docs/images/wechat-zhuatech.png" height="200" alt="知华科技微信咨询 zhuatech"><br>微信：zhuatech</td><td align="center" width="260"><img src="docs/images/wechat-zhuatech2.png" height="200" alt="知华科技微信咨询 zhuatech2"><br>微信：zhuatech2</td></tr></table>
