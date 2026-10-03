# 质量验收

知华科技（上海如静知华信息科技有限公司） · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2。

后端45项测试（8项策略单元、37项HTTP集成）：金额不舍入、正数数量、明确UTC、夏令时歧义/缺失拒绝、半开区间与CSV公式转义；真实业务覆盖报价/限载/资源冲突、司机绑定、回单照片、异常、独立费用审核、对账、收付与关联冲正、重复提交、修订及并发冲突、本人/部门范围、账号关联和最后管理员保护。集成测试使用H2的MySQL模式，不能替代MySQL运行验收。

```sh
mvn -B -f backend/pom.xml spotless:check test package
cd frontend && npm ci --no-audit --no-fund
npm run format:check && npm run lint && npm test && npm run build
```

前端格式/ESLint、3项日期时区及金额展示测试、生产构建；完整Compose镜像构建中再次运行检查。全新MySQL卷首次迁移、健康与管理员登录通过后，执行脚本：

```sh
python3 scripts/smoke.py --base http://127.0.0.1:8101 --env .env --allow-test-writes
```

脚本会创建TEST账号、主数据及业务，不可在现有业务库执行。122项实际HTTP验收客户代录、调度派车、实际司机发车、图片去重、越权下载拒绝、全量签收/异常处理/独立费用审核、关单应收应付、超额拒绝、两会话同版本付款仅一条成功、原流水冲正、净现金与利润分离、整批导入回滚。

浏览器另外验收真实表单及各岗位按钮、菜单、回单图片、列表筛选、打印、手机宽度与中英文展示；README截图为当前运行代码，样例明确TEST标识。数据库备份恢复核对原始图片散列、登录、原运单/账单/流水；迁移校验失败阻止启动也需单独检查。只清理本次独立测试项目与卷。

发布检查：`python3 scripts/release-check.py`、`git diff --check`、Compose校验、敏感信息检查、README当前截图/原二维码散列/官网/许可核对。测试通过不等于已验证外部使用或商业成交；大规模容量、真实支付/GPS/票据及任何特定行业认证未包含。
