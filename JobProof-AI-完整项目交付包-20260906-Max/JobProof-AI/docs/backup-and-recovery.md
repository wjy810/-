# 本机部署备份与恢复验收

适用：当前单实例 Compose、`deploy/data` 本机绑定目录、MinIO `/data` 命名卷。脚本必须在持有该绑定目录的 Docker 主机运行，不支持远程 Docker context、外部数据库或多副本写入；这些环境需另行设计一致性备份。

## 备份

管理员在维护窗口运行 `sh deploy/backup.sh`。这会产生短暂不可用，不要与部署、其他备份或手工容器操作并行。脚本先检查实际容器挂载、卷存在性、工具与辅助镜像，再依次停止原本运行的 app、MinIO；捕获目录及对象卷后按 MinIO→app 顺序恢复。原本停止的服务不会被启动。

- 默认辅助镜像为 Compose 已使用的 `redis:7.4-alpine`；可用 `JOBPROOF_BACKUP_HELPER_IMAGE` 指定经过运维批准、含 `sh/tar/gzip` 的镜像。缺失镜像在停服务前拉取。应在生产预先固定并缓存可信镜像摘要。
- `JOBPROOF_BACKUP_RETENTION_DAYS` 默认 `7`。只有新归档通过检查并原子发布后才清理过期的成功归档；失败暂存文件不作为成功备份保留。
- 使用 `backups/.backup.lock` 防止重复执行。异常断电/SIGKILL 无法触发退出清理；遇到遗留锁，先确认没有备份进程、核实服务状态及未发布暂存文件，再由管理员移除锁。不要直接反复删锁重跑。
- 完成后检查 app 和 MinIO 的健康状态、备份退出码，并把成功归档复制到独立、访问受控的离机存储。本机保留不是灾难恢复。

输出为权限受限的 `deploy/backups/jobproof-时间-进程号.tar.gz`，内含 `manifest.txt`、`SHA256SUMS`、`app-data.tar.gz`、`minio-data.tar.gz`。MinIO 卷名来自容器实际挂载，不猜测 Compose 前缀。退出/中断路径会尝试恢复原运行状态，启动失败会报错并返回非零；脚本不能保证主机/守护进程失效时恢复服务。

备份包含数据库和私有用户文件，应按敏感数据管理。`.env`、云端凭据、应用镜像、Compose/Nginx 配置不打入归档；必须通过单独的受控配置/密钥保管机制恢复。不要把它们写入工单、日志或恢复报告。Redis 验证码/会话缓存与 ClamAV 可重新生成的数据不属于此恢复包。

## 隔离恢复演练

1. 准备隔离主机或独立 Compose 项目、全新空数据目录与全新 MinIO 卷；关闭外发短信、邮件、模型及定时任务。保留原环境不变，绝不覆盖正在使用的卷或数据库。
2. 使用可信归档，在独立目录先列出成员并确认只有上列四个文件，再解包外层；运行 `sha256sum -c SHA256SUMS`。分别列出两个内层归档，检查绝对路径和 `..` 路径后才解包。失败即停止，不能拿损坏包继续恢复。
3. app/MinIO 均保持停止。把 `app-data.tar.gz` 恢复到新绑定目录，把 `minio-data.tar.gz` 恢复到新命名卷；保留文件属主、权限和 MinIO 元数据。按 manifest 的实际源卷与新卷逐项人工核对，不能仅凭名称相似选卷。
4. 从受控渠道恢复对应应用版本和配置。MinIO 使用与备份源兼容的版本及凭据，app 使用对应数据库版本；先启动 MinIO并确认健康，再启动 app。上线前才恢复已审核的外部连接。
5. 验收数据库启动及迁移状态、账号隔离、简历内容、附件下载/预览；抽查多个数据库文件引用均能读取对象，对比原始文件 SHA-256。检查一个数据库引用存在但对象缺失的反例会被明确识别。记录恢复耗时、文件数量/大小、抽样哈希和差异，不记录个人原文或密钥。
6. 演练通过后才确定切换方案和回滚点。演练失败时保持原环境运行，仅保留隔离演练资源供排查；删除任何卷前另行核对并授权。

## 本次验证边界

`sh deploy/tests/backup-mock-test.sh` 不接触真实 Docker，覆盖完整归档、对象归档失败后的启动恢复/不发布、缺失卷的停机前拒绝，以及原本停止状态。

2026-09-05 在本机 WSL + Docker Desktop 执行了 `sh deploy/tests/backup-real-drill.sh`：新建完全隔离的 source/restored Compose 项目、新空目录、新 MinIO 命名卷，未读取用户业务数据、未停止其他项目容器。使用原 `backup.sh` 真正停止/恢复合成源容器、归档 SQLite 文件及真实 MinIO 卷，在新资源解包后重新启动 MinIO，S3 下载并校验两个对象。外层成员检查、内层路径检查、SHA256SUMS、SQLite `PRAGMA integrity_check`、数据库字节一致、2 个数据库引用对象哈希和 1 个故意缺失对象反例均通过。

- 项目：`jobproof-auditdrill-20260905201613-1782-source` / `jobproof-auditdrill-20260905201613-1782-restored`。
- 证据：WSL Ubuntu `/tmp/jobproof-auditdrill.xm4D1w/result.json`、`resources.txt`、`missing-reference.txt` 与源 `backups` 归档；脚本保留资源用于复核，不自动删除。两个项目容器在验收后停止，卷仍保留。
- 对象：`resume.txt` 30 字节，SHA-256 `38d9e9a2d5ac0a2d33aca155b6d2abb58be4557796881aba791044ea0de3f930`；`attachment.bin` 8192 字节，SHA-256 `dc404a613fedaeb54034514bc6505f56b933caa5250299ba7d094377a51caa46`。

**边界：这是合成 SQLite 引用 + MinIO 的真实归档恢复演练，不是正式应用 H2 数据库/迁移/账号隔离端到端恢复。正式应用版本启动、真实脱敏数据恢复、迁移与业务验收、离机恢复、RPO/RTO 仍需管理员在隔离环境安排。未执行远程恢复或线上切换。**

2026-09-05 22:05 又从当前磁盘脚本独立重跑一次相同演练，14 秒完成。新项目 `jobproof-auditdrill-20260905220507-3000-source` / `jobproof-auditdrill-20260905220507-3000-restored` 的外层成员、两个内层归档路径、SHA-256、SQLite 完整性、两个对象哈希及故意缺失引用均再次通过；四个合成容器最终均为 `exited`。证据保留于 WSL `/tmp/jobproof-auditdrill.lkTv86/`，未读取或变更正式应用数据，边界仍与上段一致。

## 2026-09-06 真实 H2 续验状态

重启后 WSL `/tmp` 被清空，上述两次 SQLite 演练的临时文件已不可重新读取，本文保留当时结果。新增的真实 H2 脚本改用 `/var/tmp/jobproof-h2drill.*`，完成后应把 `result.json`、`resources.txt`、应用日志及校验值另行复制到持久验收目录。

`deploy/tests/backup-h2-drill.sh <已验证应用 JAR> <隔离 ClamAV 容器名称>` 使用真实应用、Flyway、文件 H2、MinIO 和 ClamAV，创建两个合成账号、简历和 PNG 资料，调用现有 `backup.sh` 备份，再恢复到新目录及新对象卷。验收目标包含密码登录、简历内容、原始文件与预览 SHA-256、跨账号访问拒绝。网络为内部网络，不发布宿主端口，不读取用户业务数据；脚本保留资源并尝试停止自己创建的 source/restored 容器，各 Docker 调用有超时以免守护进程失效时无限等待。

本次执行使用 JAR SHA-256 `03599AE8C86B848A1785858F32C3F469910A8703196B51A29FD124CCDFD9BE65`。源项目 `jobproof-h2drill-20260906043845-504-source` 的应用已返回 `UP`，临时目录为 `/var/tmp/jobproof-h2drill.XVwQWr`。随后共享 Docker/WSL 失去响应，WSL 返回 `Wsl/Service/0x8007274c`，Docker 日志显示虚拟机地址 `192.168.65.7:2376` 不可达。**本次真实 H2 恢复尚未通过，不存在成功的 result.json，不能作为恢复完成的证据。**

已请求停止本次 source app、source MinIO 和隔离 ClamAV，但三个停止请求均返回 Docker API 500，尚不能确认最终停止状态。恢复 Docker 后先核对这三个明确命名的资源，再在隔离环境执行脚本。未执行全局 Docker reset、prune、卷删除或任何生产操作。当前脚本已通过 shell 语法检查，完整运行验收待 Docker 环境恢复。

2026-09-06 05:20 CST 做了一次有界只读状态复核：Docker/WSL 在 15 秒内未响应，检查按超时结束；未重复轮询或重启共享虚拟机。H2 验收及上述三个隔离容器的最终停止状态仍待环境恢复后核实。
