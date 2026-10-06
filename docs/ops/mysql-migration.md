# 从 H2 文件库迁移到 MySQL

适用：已经按旧版 `deploy/docker-compose.yml` 运行、数据在 `deploy/data/jobproof.mv.db`（H2 文件库）的单机部署。新版本的生产数据库是 MySQL 8.4（Compose 内置 `mysql` 服务，或托管数据库）。迁移需要一个维护窗口，期间服务不可用。

## 0. 原则

- 只读源库：迁移工具以只读方式打开 H2 文件，不修改它。旧数据目录保留到新环境验收通过、且至少完成一次新格式备份之后。
- 只写空库：目标库必须是空的。工具发现目标库已有业务数据时直接拒绝，不会合并或覆盖。
- 版本对齐：工具先把目标库迁移到与源库完全相同的 Flyway 版本，再复制数据并逐表核对行数；应用下次启动时才把目标库升级到最新版本。
- 任何一步失败都停止，保持旧环境可回滚，不要手工补数据。

## 1. 准备

1. 用旧版 `backup.sh` 做一次完整备份并复制到离机存储（见 [backup-and-recovery.md](backup-and-recovery.md)）。
2. 在 `deploy/.env` 增加 `JOBPROOF_DB_PASSWORD`、`JOBPROOF_DB_ROOT_PASSWORD`（随机强密码，单独保管）。使用托管数据库时改为设置 `JOBPROOF_DB_URL`、`JOBPROOF_DB_USERNAME`、`JOBPROOF_DB_PASSWORD`，并删除 Compose 里的 `mysql` 服务；托管库需为 MySQL 8.x、`utf8mb4`。
3. 拉取新版本代码与镜像，**先不要启动 app**。

## 2. 停服并启动 MySQL

```sh
docker compose stop app
docker compose up -d mysql
docker compose ps mysql   # 等待 healthy
```

## 3. 复制数据

在新版本镜像中运行迁移工具（源库只读挂载）：

```sh
docker compose run --rm --no-deps \
  -v "$PWD/data:/var/lib/jobproof-h2:ro" \
  -e SOURCE_URL='jdbc:h2:file:/var/lib/jobproof-h2/jobproof;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;ACCESS_MODE_DATA=r' \
  -e SOURCE_USER=sa -e SOURCE_PASSWORD= \
  -e TARGET_URL='jdbc:mysql://mysql:3306/jobproof?useUnicode=true&characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true' \
  -e TARGET_USER=jobproof -e TARGET_PASSWORD="$JOBPROOF_DB_PASSWORD" \
  --entrypoint java app \
  -Dloader.main=com.jobproof.tools.DatabaseCopyTool -Duser.timezone=Asia/Shanghai \
  -cp /app/jobproof-backend.jar org.springframework.boot.loader.launch.PropertiesLauncher
```

输出依次是源库版本、目标库迁移结果、每张表复制的行数，最后一行是 `verified N tables, M source rows; mismatches: 0`。退出码 0 表示所有表行数一致；非 0 时保留输出、不要启动 app，先排查。

只核对、不写入（例如重跑确认）时在命令末尾加 `--verify-only`。

## 4. 启动与验收

```sh
docker compose up -d app
docker compose logs -f app   # 确认 Flyway 把目标库升级到最新版本、应用 UP
```

验收清单：

- 管理员与抽样求职者账号能登录；会话是新的（旧登录需要重新登录）。
- 抽样简历的内容、版本、导出文件能打开；资料库文件能预览和下载（对象仍在 MinIO，未迁移）。
- 岗位匹配、模拟面试、职业规划的历史记录能打开。
- 立刻运行新版 `sh deploy/backup.sh`，确认归档包含 `mysql-dump.sql.gz`、`manifest.txt` 中 `format=jobproof-backup-v3`。

## 5. 回滚

新环境验收失败时：`docker compose stop app`，切回旧版本代码与 `.env`，旧 H2 数据目录未被修改，直接启动旧版本即可。迁移期间产生的新数据（如有）不会自动回流。

## 6. 验证记录

2026-10-06，在本机 MySQL 8.4.11 容器上演练（非生产数据）：

- 源：开发环境的真实 H2 文件库副本（Flyway 版本 69）。工具把空的 MySQL 库迁移到 69，复制 123 张表、1727 行，`verified 123 tables, 1727 source rows; mismatches: 0`，退出码 0。
- 随后用打包后的应用以该 MySQL 库启动：Flyway 自动升级到 70，应用正常启动；原有求职者账号用原密码登录，完成工作台建简历、换模板、一键紧凑、导出 PDF（招聘系统可读性检查全部通过），以及一次完整的模拟面试和报告查看。
- 对已有数据的目标库重跑会被拒绝（单元测试覆盖）。

未覆盖：生产服务器上的实际迁移由管理员在维护窗口执行；MinIO 对象不在本工具范围内（对象存储不变）。
