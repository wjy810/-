# JobProof AI 后端

Java 17 + Spring Boot 3 模块化单体。当前产品核心为 AI 简历、AI 岗位匹配、模板中心、模拟面试和求职资料库。岗位匹配使用独立任务域完成 JD 解析、简历确认、证据授权、规则与 AI 分析、澄清、报告和行动联动；已删除的“岗位资料”独立栏目和投递管理不恢复。传统面试/复盘、独立画像和旧证据库保持退役，迁移前数据仅保留在账户历史归档中。标准岗位分类树继续作为 AI 简历、岗位匹配和模拟面试的基础数据。Worker 默认同进程运行。

## 环境

- JDK 17
- Maven 3.8+
- 可选：Docker（MySQL 8 + Redis + ClamAV）

禁止用 Python 作为项目运行方式。

## 启动（无 MySQL / Redis）

在 `backend/` 目录：

```bash
mvn test
mvn spring-boot:run
```

默认 `dev` profile：H2 文件库（`.local-data/jobproof`），不连 Redis。进程内 Worker 轮询导出/删除任务，以及 JD 解析、资料文件处理、简历导入、岗位匹配分析、简历定制和 PDF/DOCX 导出。
`dev`/`test` 使用仓库内仅供本机的固定 AI 密钥；非开发环境必须通过 `JOBPROOF_AI_MASTER_KEY` 注入独立的 32 字节 Base64 密钥，禁止复用该本地值。

健康检查：`GET http://localhost:8080/api/v1/health`

开发验证码邮箱（仅 dev/local/test）：`GET http://localhost:8080/internal/dev/mailbox/{email}`

## 启动（Docker MySQL 8 + Redis + ClamAV）

```bash
docker compose up -d
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

`local` 使用 compose 内的本地默认口令，**不得用于生产**。
ClamAV 首次启动需要下载病毒库；健康检查通过后，`local` profile 才能把候选 DOCX 扫描为可信。
默认 `dev` profile 不假定存在外部扫描器，候选文件会进入隔离命名空间并保持不可审批。
非本地环境必须显式配置 `JOBPROOF_TEMPLATE_MALWARE_SCAN_ENABLED=true`、
`JOBPROOF_TEMPLATE_MALWARE_SCAN_HOST` 和 `JOBPROOF_TEMPLATE_MALWARE_SCAN_PORT`；连接失败、超时、
未知响应或病毒命中都按失败关闭处理，不能用静态 OOXML 检查替代独立恶意文件扫描。

## 构建与运行边界

`mvn -B verify` 执行完整测试并生成 `target/jobproof-backend.jar`；正常运行入口为 `java -jar target/jobproof-backend.jar`。在 Windows 上，不要对正在运行的同一 JAR 执行 `mvn clean`；先停止该实例，或把验收用 JAR 复制到独立运行目录。

`JobProofWorkerApplication` 和 `application-worker.yml` 仍是独立 Worker 骨架，尚未完成独立部署验收；正式部署继续使用默认同进程 Worker。不再提供把普通主类直接传给 Spring Boot fat JAR 的无效 `-cp` 命令。

隔离验收使用 `test` profile、`127.0.0.1:18081` 和内存 H2，不读取开发/生产数据库。测试账号通过 `jobproof.dev.seeker.*` 显式启用；岗位匹配和职业规划需分别启用 `jobproof.job-match.enabled`、`jobproof.career-planning.enabled`。如前端为 `127.0.0.1:5174`，还需设置 `--jobproof.cors.origins=http://127.0.0.1:5174`。这些本地设置不得用于生产。

## 平台 AI 通道

管理员在 `/admin/ai-channels` 填写名称、HTTPS Base URL、协议和 API 密钥即可保存草稿，供应商代码可留空。首次点击“检测可用模型”后，系统按发现顺序逐个测试，跳过请求失败、无有效流式正文或无法输出 JSON 的模型，记录首个全部通过的模型。发现的模型在可搜索下拉框中展示，默认选中当前已启用模型；已有选择时只检测所选模型。检测最多持续 120 秒，检测和选择都不会切换当前模型，点击“启用所选模型”才生效；如果所选模型尚未通过检测，该操作会先检测再启用。刷新页面保留当前生效模型和最近的检测结果。

平台功能默认使用 `auto`，直接调用已启用通道选中的实际模型，不要求预建模型目录、价格或别名映射。`JOBPROOF_RESUME_MODEL`、`JOBPROOF_JOB_MATCH_MODEL`、`JOBPROOF_CAREER_PLANNING_MODEL` 可指定固定模型；显式指定的模型不会被自动替换成其他模型。旧客户端仍可传可选的 `platformModelCode` 使用原有别名映射。

V86 迁移会保留旧通道最近一次测试通过的模型。V87 将旧客户端的可选别名保存在检测记录中，点击启用时才应用映射。启用接口可传 `providerModel`，后端校验它与最近一次通过检测的模型一致，并通过 `expectedVersion` 防止覆盖并发修改。开发通道首次启动以 `JOBPROOF_DEV_AI_MODEL` 填充默认模型，后续重启保留管理员已启用的模型和通道停用状态。自动发现不会复制其他模型的价格或修改历史账单。

## 模块包

- `modules.identity` 邮箱/手机号账号、HttpOnly Cookie 会话、验证码注册与密码重置、登录节流
- `modules.datarights` 导出任务、删除编排、只读限时分享
- `modules.task` 长任务五态与幂等
- `modules.notification` 站内通知与去重
- `modules.audit` 安全/数据权利痕迹（不含原文）
- `modules.storage` 本地/MinIO 私有对象存储、文件类型策略与鉴权下载
- `modules.career` 求职资料主档、结构化职业记录、私有资料文件、逐页预览与历史归档
- `modules.job` JD 原文快照、启发式解析候选、岗位版本确认/归档
- `modules.jobmatch` 岗位匹配任务、证据授权、规则与 AI 分析、澄清、版本化报告和导出
- `modules.resume` 简历主档、AI 候选、冻结版本、定制任务、PDF 导出
- `modules.resumeimport` PDF/DOCX/文本简历提取、结构化校正确认
- `modules.airesume` AI 对话工作台、会话资料引用、分支、修订与导出协调
- `modules.aigateway`、`modules.aiconfig`、`modules.aibilling` 模型通道、协议适配、健康与额度
- `modules.careerplanning` 职业方向、能力画布、学习计划、验证与版本
- `modules.mockinterview` 文字/语音模拟面试、草稿、转写与反馈

`modules.datarights` 负责岗位匹配数据在内的账号导出和删除清理。历史 `match_reports` 保持只读兼容，新业务通过 `modules.jobmatch` 的公开接口访问。`docs/plan` 下的 S0-S5 文档是历史交付记录，用于解释旧迁移和历史数据，不代表当前开放的产品模块。
