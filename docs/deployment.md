# 部署、升级与恢复

知华科技（上海如静知华信息科技有限公司） · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2。商业部署须先取得书面授权。

## 本机与HTTPS部署

Docker Compose v2、Docker Engine、可拉取MySQL8.4/Maven Java21/Node24/Nginx1.29镜像的网络，建议4GB以上内存。执行 `python3 scripts/init-env.py` 生成本机私有.env，不输出密码；查看文件取得管理员密码，勿提交。可在私有文件修改WEB_PORT解决占用。

```sh
docker compose config --quiet
docker compose up --build -d --wait --wait-timeout 240
docker compose ps
curl --fail http://127.0.0.1:8101/actuator/health
```

数据库与后端仅内部网络可达。前端默认本机8101。在受控HTTPS反向代理后部署，配置证书、可信转发来源与访问限流、将COOKIE_SECURE设true；不要直接公开HTTP管理端。远程手机需要可达的HTTPS域名及网络，不会依赖源码发布平台的VPN环境。外部MySQL使用MariaDB JDBC URL与verify-full、可信CA；不要使用未知网络trust。

## 数据持久化与备份

mysql-data卷保存所有业务、照片和账号。`docker compose down`保留卷；`down -v`会删除数据，仅可用于本次可丢弃测试环境。严禁为排错删除业务卷。

先暂停写入、备份并校验可恢复。在私有安全目录执行，SQL包含敏感业务，设0600并加密离线保存；不要提交仓库。

```sh
umask 077
docker compose exec -T mysql sh -c 'exec mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" --single-transaction --hex-blob --no-tablespaces --routines --triggers "$MYSQL_DATABASE"' > freightflow-backup.sql
```

`--hex-blob`保证原始回单完整。备份同时保管版本号、迁移历史、部署设置和密钥，实施访问控制与保留期限。正式恢复应在新隔离库先验证，再切换：初始化同名空库，用root导入备份，启动对应版本后端并核对health、登录、运单、图片SHA-256、账单与原流水。

```sh
# 仅对准备好的隔离恢复数据库执行，先确认Compose项目名与卷。
docker compose exec -T mysql sh -c 'exec mysql -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE"' < freightflow-backup.sql
```

这是恢复已有完整数据库；不能叠加导入到正在使用的业务库。后端只在空account表创建管理员，因此恢复库沿用原密码，环境变量不是密码重置工具。

## 版本升级

先备份并实际验证恢复，再对隔离副本构建升级，观察Flyway新增版本与JPA结构校验、health、账号权限及原业务。禁止修改已执行V1、删除Flyway历史或repair掩盖校验差异。升级脚本独立版本，避免破坏现有单据。正式切换需维护窗口；恢复使用旧版镜像及升级前已验证的数据库备份。前端、后端与迁移版本一起发布。

后端镜像使用锁定Maven缓存、依赖预取与网络重试，spotless和完整测试不跳过。前端镜像执行格式/代码/测试/生产构建。非root运行服务，Nginx通过容器服务名代理，不公开后台内部端口。运行版本与依赖精确版本以pom/package-lock/Dockerfile为准。
