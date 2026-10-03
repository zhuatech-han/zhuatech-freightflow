# 数据库与迁移

知华科技（上海如静知华信息科技有限公司） · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2。

MySQL8.4；完整建表和约束见 `backend/src/main/resources/db/migration/V1__freight_schema.sql`。Flyway首次建立并执行V1，JPA validate校验实体；不使用自动结构更新。

| 领域 | 表 |
| --- | --- |
| 组织认证 | department、access_role、role_permission、account、permission、nav_menu |
| 配置与审计 | dictionary_entry、system_setting、audit_event |
| 主数据 | customer、carrier、driver、vehicle |
| 运输 | shipment、trip、transport_event、delivery_proof |
| 财务 | expense、invoice、invoice_line、settlement_entry |
| 重试保护 | mutation_stamp |

22张应用表加Flyway历史表。账号链接、资源链接、单据关系及流水来源使用外键。容量和费用非负校验、业务单号及关联账号唯一约束、部门状态与车次索引见迁移脚本。照片使用LONGBLOB；金额DECIMAL(18,2)，重量体积DECIMAL(18,3)。业务原始流水只增不改，反向流水通过source_id保留关系。

启动时初始化5个角色、14项权限、18个菜单、1个部门、3个付款字典及3个系统参数；管理员只在空库写入，密码由外部环境随机生成并BCrypt加密。SEED_DEMO仅在新库建立标记DEMO的基础档案，无模拟收入。驱动使用MariaDB JDBC连接MySQL。

升级必须增加V2、V3等文件，不改已执行V1；先备份与验证恢复，再升级并校验health、登录、权限和原单据。已有库迁移校验失败会阻止启动，不自动repair或清库。详细恢复步骤见部署文档。
