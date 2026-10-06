# JobProof 求职者平台：仓库审计与项目改进计划

审计日期：2026-09-05。本文保留初次只读审计快照，以下问题、行号、测试数量和暂停状态描述的是审计当时，不是当前源码状态。后续用户已授权修复并取消 30 分钟上限；最新修复、验证证据和剩余边界统一见 [实施记录](../visual/2026-09-05-implementation-status.md)。未部署生产服务器。

## 执行摘要

1. **整体健康等级：D（判断）**：功能与测试基础已经成形，但凭据、传输安全、文件恢复和编辑一致性存在多项高风险，暂不具备可靠的持续发布条件，依据见 F01–F08。
2. 这是一套面向求职者、带运营管理端的早期生产应用，而非只有静态页面的原型；用途和模块依据为 [后端 README:3](../../backend/README.md:3) 与 [前端路由:117](../../frontend/src/app/router.ts:117)。
3. 第一风险是主源码包含与本地部署配置相同的供应商凭据，且所提供生产配置使用明文 HTTP，见 F01、F02；没有验证远程凭据是否仍有效，也没有推断服务器外部是否存在额外防护。
4. 第二风险是模板切换、异步保存和数据库并发更新可能静默覆盖用户的新内容，见 F04–F06。
5. 第三风险是备份遗漏 MinIO 对象卷，数据库恢复成功也不代表用户文件恢复成功，见 F03。
6. 前三项改进机会是：统一可验证的保存语义、建立源码到发布包的测试门禁、打通数据库与私有文件的恢复演练，分别对应 T05–T08、T01/T13/T14、T02。
7. 本轮实际检查得到前端逻辑测试 **122/122**、组件测试 **40/40** 和不产出文件的类型检查通过；后端完整测试 **306 项、3 失败、0 错误、1 跳过**，详见执行记录及 F08。
8. 本文保留 **15 项高置信度发现：高 8 项、中 6 项、低 1 项**；没有已验证的“严重”级远程利用结论，也不把 npm 查询零漏洞等同于整个系统安全。

## 审计范围、阶段与证据规则

- 实际项目根目录为本交付包根目录；审查对象是这个主项目的前端、后端、部署配置和开发资料，未擅自扩大到其他个人项目。
- 按用户要求使用三个不重叠的子代理：`backend_audit` 负责业务服务、数据一致性及后端控制流；`security_ops_audit` 负责身份、安全、文件访问及部署；`frontend_audit` 负责客户端状态和交互。主代理负责清单、依赖、测试执行、文档、证据复核和最终合并。
- 阶段 1 先收集各区域地图，再统一发出阶段 2 开始指令；阶段 2 的结果之后才用于阶段 3 的策略与阶段 4 的任务计划。跨代理只交换接口事实，不重复分配同一审计领域。
- 本任务曾发生会话中断。本次接续复用已完成的多代理发现和测试，不重新启动相同审计，不把中断期间算成持续执行，也不声称首次开始到最终交付的墙钟时间小于 30 分钟；本次接续限定在 30 分钟内，交付后暂停。
- “事实”表示文件、执行输出或受控复现实验直接支持的内容；“判断”表示基于这些事实推导的后果、评级和建议。静态并发路径分析、内存模拟、完整测试、生产验证是不同证据强度。
- 行号来自本次读取的文件。测试报告在 `backend/target/surefire-reports/`，后续测试可能覆盖它们；本文保留关键结果，但没有 Git 提交号可绑定本快照。
- 只新增本文档；未修改业务源码、测试源码、锁文件、配置、数据库或服务器。测试生成的 `target/` 等临时产物属于执行结果，不是代码修复。

## 仓库地图（阶段 1）

### 目的、技术栈与运行目标

| 项目 | 实际证据与结论 |
| --- | --- |
| 产品目的 | AI 简历、资料管理、模板中心、岗位匹配、模拟面试；职业规划另有完整交互模块。见 [backend/README.md:3](../../backend/README.md:3)、[职业规划验收说明:5](../../docs/career-planning-visual-acceptance.md:5)。 |
| 前端 | Vue 3、TypeScript、Vue Router、Vite，页面按路由懒加载。见 [package.json:16](../../frontend/package.json:16)、[router.ts:26](../../frontend/src/app/router.ts:26)。 |
| 后端 | Java 编译目标 17、Spring Boot 3.3.13、Maven；同时使用 JPA 和 JdbcTemplate。见 [pom.xml:7](../../backend/pom.xml:7)、[pom.xml:20](../../backend/pom.xml:20)、[CareerLibraryService.java:90](../../backend/src/main/java/com/jobproof/modules/career/application/CareerLibraryService.java:90)。 |
| 数据与任务 | 单体 API 内默认运行定时 Worker；H2、MySQL 支持取决于 profile，生产文件配置默认 H2 文件库，私有对象使用 MinIO，验证频控使用 Redis。见 [README:22](../../backend/README.md:22)、[application-prod.yml:1](../../backend/src/main/resources/application-prod.yml:1)、[application-prod.yml:31](../../backend/src/main/resources/application-prod.yml:31)。 |
| 部署 | Docker Compose 编排 app、nginx、Redis、MinIO、ClamAV；Java 17 JRE 运行预构建 JAR，镜像中安装 LibreOffice、Tesseract 与中文字库。见 [docker-compose.yml:3](../../deploy/docker-compose.yml:3)、[Dockerfile:1](../../backend/Dockerfile:1)。 |
| 本地检查环境 | 本轮测试执行输出显示 Node 24.6.0、npm 11.5.1、JDK 21.0.12.1；它们并不等同于 Docker 的 Java 17 目标。目标一致性应由 T01/T13 固定。 |

### 主要控制流

```text
浏览器 / 手机
  └─ Vue 路由与 AppChrome
       └─ 统一 JSON / SSE / 下载客户端
            └─ Nginx → Cookie 会话验证 → Controller → 应用服务
                 ├─ 求职资料 / 简历主档 → AI 候选 → 用户确认 → 修订和冻结版本
                 ├─ 岗位匹配 / 职业规划 / 模拟面试
                 └─ 数据库任务 → Worker → PDF / DOCX / 文件预览 → 私有对象存储
```

入口与流程证据：[main.ts:18](../../frontend/src/main.ts:18)、[client.ts:68](../../frontend/src/shared/api/client.ts:68)、[SessionAuthFilter.java:39](../../backend/src/main/java/com/jobproof/infrastructure/security/SessionAuthFilter.java:39)、[JobProofApiApplication.java:14](../../backend/src/main/java/com/jobproof/JobProofApiApplication.java:14)、[ResumeService.java:557](../../backend/src/main/java/com/jobproof/modules/resume/application/ResumeService.java:557)。

### 关键目录与惯例

| 目录 | 一行说明与代表证据 |
| --- | --- |
| `frontend/src/app` | 路由与访问条件，公开首页、登录弹层入口和受保护功能分开，[router.ts:29](../../frontend/src/app/router.ts:29)。 |
| `frontend/src/features` | 按产品领域组织页面、API 封装和工具函数，页面内大量 `ref/reactive` 状态，[session.ts:7](../../frontend/src/features/identity/session.ts:7)。 |
| `frontend/src/shared` | 通用控件、响应式外壳、错误与下载处理，[AppChrome.vue:30](../../frontend/src/shared/ui/AppChrome.vue:30)、[client.ts:185](../../frontend/src/shared/api/client.ts:185)。 |
| `backend/src/main/java/com/jobproof/modules` | 按领域分包的模块化单体；较新的资料库、AI 工作台和职业规划直接组织 JDBC、JSON 与业务操作，[CareerPlanningService.java:125](../../backend/src/main/java/com/jobproof/modules/careerplanning/application/CareerPlanningService.java:125)。 |
| `backend/src/main/java/com/jobproof/infrastructure` | Spring 配置、安全过滤、统一响应与异常处理，[SecurityConfig.java:32](../../backend/src/main/java/com/jobproof/infrastructure/security/SecurityConfig.java:32)。 |
| `backend/src/main/resources/db/migration` | Flyway SQL 管理版本、来源、任务与旧业务退役，例如 [V21:24](../../backend/src/main/resources/db/migration/V21__ai_resume_workbench.sql:24)。扫描得到 54 个迁移文件，最高编号 V66；编号不连续本身不被判定为缺陷。 |
| `backend/src/test`、`frontend/e2e` | 后端单测与 IT 同属 Surefire；前端另有逻辑、组件和 Playwright 测试，[pom.xml:139](../../backend/pom.xml:139)、[playwright.config.ts:23](../../frontend/playwright.config.ts:23)。 |
| `deploy` | Compose、Nginx、备份与本地运行环境配置，[backup.sh:4](../../deploy/backup.sh:4)。 |
| `docs`、`开发文档` | 历史实施计划与验收证据，不能全部当作当前产品契约，[backend/README.md:70](../../backend/README.md:70)。 |
| `简历模板`、`借鉴项目*` | 下载模板资产与参考项目；未作为独立产品代码深入审计，模板许可抽查见 [LICENSE:5](../../简历模板/hicv-word-resume-templates-main/hicv-word-resume-templates-main/LICENSE:5)。 |

**发现阶段值得注意的事实：**项目扫描得到后端主源码 314 个文件、后端测试区 85 个文件、前端源码区 231 个文件。当前目录未发现 `.git`、根 README、项目内 AGENTS/CONTEXT、`.github/workflows` 或 `docs/adr`；`git rev-parse --show-toplevel` 也返回不是 Git 仓库。这个“未发现”只描述交付到本机的目录，不证明其他位置没有远程仓库或 CI。构建入口仍直接使用 `target/jobproof-backend.jar`，见 [Dockerfile:16](../../backend/Dockerfile:16)。

### 审查深度与未覆盖项

深入核心约 20% 的代码路径：身份、文件访问、AI 简历确认/保存/流式输出、资料库写入、模拟面试保存与评分、部署及测试入口。职业规划整体架构和 SSE 测试做了抽查；管理员模板审批、完整 PDF 排版算法、计费结算、账户删除、所有历史迁移及所有供应商协议未逐行审完。未执行生产攻击测试、服务器资源采样、完整恢复演练、真实短信/邮件/模型调用、MySQL 全量迁移或真实手机录音验证。没有证据的部分不会标成通过。

## 审计报告（阶段 2）

### 安全：按严重性排序

#### F01 · 高 · 主程序源码包含部署凭据

- **事实／位置：**[DevVerificationProviderConfiguration.java:19](../../backend/src/main/java/com/jobproof/infrastructure/config/DevVerificationProviderConfiguration.java:19)、同文件 23–24 行包含 SMTP 授权码及阿里云访问密钥字面量。安全代理仅输出相等/不相等的本地比较，确认对应值与 `deploy/.env:13,16,17` 相同；报告不收录任何值。
- **判断／后果：**获得源码或包含此类的构建包的人能够提取同一组部署凭据；`@Profile` 只控制 Bean 激活，不阻止类或常量进入构建包。应按已暴露处理，轮换并移出源码；没有验证远程凭据当前仍可用。
- **验证范围：**源码与配置比较；未调用供应商，也未检查历史分发范围。关联 T03。

#### F02 · 高 · 提供的生产配置仅支持明文 HTTP

- **事实／位置：**[nginx.conf:2](../../deploy/nginx.conf:2) 监听 80；[docker-compose.yml:92](../../deploy/docker-compose.yml:92) 发布 80；[application-prod.yml:25](../../backend/src/main/resources/application-prod.yml:25) 默认 `secure=false`，本地部署环境同样设置 HTTP 和关闭 Secure。Cookie 的实际属性由 [SessionAuthFilter.java:56](../../backend/src/main/java/com/jobproof/infrastructure/security/SessionAuthFilter.java:56) 写入。
- **判断／后果：**直接按这套配置对外提供服务时，登录数据、会话和私有资料无法获得 TLS 的链路保密性。需要 HTTPS 入口、重定向、正确的转发协议和 Secure Cookie。
- **验证范围：**配置证据；未核实云端是否另有 TLS 终止层，因此不是对所有线上路径的抓包结论。关联 T04。

#### F09 · 中 · 离开面试页面后，迟到的麦克风授权仍可启动录音

- **事实／位置：**[MockInterviewSessionPage.vue:413](../../frontend/src/features/mock-interview/pages/MockInterviewSessionPage.vue:413) 等待 `getUserMedia` 后直接创建并启动录音器、计时与转写；[同文件:551](../../frontend/src/features/mock-interview/pages/MockInterviewSessionPage.vue:551) 的卸载只清理当时已存在的流，没有使仍在等待的请求失效。
- **复现：**前端代理用实际函数和延迟返回的假媒体对象，在“申请权限 → 卸载 → 返回授权”顺序下观察到录音启动、音轨未停止、数据块回调进入上传路径。没有使用真实麦克风或发送音频。
- **判断／后果：**用户已离开页面，却可能继续采集音频且看不到原停止控件。应使用卸载标记或生命周期代次，收到迟到流立即停止。关联 T09。

#### F11 · 中 · 验证码按 IP 的限额会在当前反代配置下合并

- **事实／位置：**[ContactVerificationController.java:40](../../backend/src/main/java/com/jobproof/modules/identity/web/ContactVerificationController.java:40) 使用 `getRemoteAddr()`，其值进入 [VerificationRateLimitService.java:45](../../backend/src/main/java/com/jobproof/modules/identity/application/VerificationRateLimitService.java:45) 的小时计数键。Nginx 在 [nginx.conf:32](../../deploy/nginx.conf:32) 发转发头，但所审代码、配置和部署环境未发现转发头解析设置。
- **判断／后果：**按该 Compose 运行时，应用看到的来源会是代理，邮件每小时 50 次、短信每小时 10 次的 IP 上限可能变成站点共享上限；正常注册或找回密码也会受影响。默认数值见 [application.yml:79](../../backend/src/main/resources/application.yml:79)。
- **验证范围：**静态代理链分析，未发真实短信压测。只信任明确代理来源，不能直接相信任意客户端提供的 X-Forwarded-For。关联 T11。

#### F12 · 中 · 密码登录缺少失败次数与请求频率控制

- **事实／位置：**[AuthController.java:64](../../backend/src/main/java/com/jobproof/modules/identity/web/AuthController.java:64) 进入 [IdentityService.java:191](../../backend/src/main/java/com/jobproof/modules/identity/application/IdentityService.java:191) 后直接查询账号、验证 BCrypt；这条路径没有验证码申请服务那样的计数逻辑。[nginx.conf:27](../../deploy/nginx.conf:27) 也没有 API 限流配置。
- **判断／后果：**应用层允许连续猜密并反复执行昂贵的密码校验。应使用账号和可信客户端 IP 两个维度的限制，配合失败审计与温和退避，避免永久锁定被用来攻击正常用户。
- **验证范围：**没有执行撞库或容量攻击，未知外部网关保护，故不升级为已证实的高危远程利用。关联 T12。

### 开发体验与运维

#### F03 · 高 · 备份不能恢复完整文件资产，打包失败还会跳过重启

- **事实／位置：**[backup.sh:9](../../deploy/backup.sh:9) 停 app，10 行只归档 `./data`，11 行才重启。生产对象存储配置位于 [application-prod.yml:38](../../backend/src/main/resources/application-prod.yml:38)，MinIO 对象在独立 [minio-data 卷:29](../../deploy/docker-compose.yml:29)，没有进入该 tar。脚本使用 `set -eu` 且无退出恢复处理。
- **判断／后果：**只恢复这个归档会得到数据库引用，却缺失新上传或已迁入 MinIO 的文件；磁盘满、权限等导致 tar 失败时，app 还可能一直保持停止。现有 legacy 本地对象回退不能保证包含所有 MinIO 对象。
- **验证范围：**未实际停止服务或恢复备份。应做一致性备份、失败后恢复原运行状态，并在独立环境验证文件哈希和引用。关联 T02。

### 架构与设计

#### F06 · 高 · 手动版本校验与数据库写入没有组成原子操作

- **事实／位置：**[CareerLibraryService.java:78](../../backend/src/main/java/com/jobproof/modules/career/application/CareerLibraryService.java:78) 先读取版本，再在 90 行用仅包含 `account_id` 的条件更新；[同文件:188](../../backend/src/main/java/com/jobproof/modules/career/application/CareerLibraryService.java:188) 对记录也先校验、后在 196 行仅按 id/account 更新。[requireRecord:548](../../backend/src/main/java/com/jobproof/modules/career/application/CareerLibraryService.java:548) 没有行锁。同类模式见 [MockInterviewService.java:474](../../backend/src/main/java/com/jobproof/modules/mockinterview/application/MockInterviewService.java:474)。
- **判断／后果：**两个请求先读到相同版本，再依次更新时，都可能通过校验，后写覆盖先写。`@Transactional` 并不会自动把普通 SELECT 和后续 UPDATE 变成比较交换操作，快照通知也可能使用读取时的旧版本。
- **验证范围：**静态可达交错分析；未做并发数据库实验。应在 UPDATE 条件中加入 expectedVersion，检查影响行数，再提交事件；需要合并多表时明确行锁顺序。关联 T07。

#### F07 · 高 · 重复流式请求的拒绝会污染原请求的回复记录

- **事实／位置：**[prepareStreaming:668](../../backend/src/main/java/com/jobproof/modules/airesume/application/AiResumeWorkbenchService.java:668) 重用同一个用户消息；原回复尚未写入时仍进入生成尝试。[AiGenerationAttemptService.java:27](../../backend/src/main/java/com/jobproof/modules/airesume/application/AiGenerationAttemptService.java:27) 拒绝已存在的前台任务，但 [respondStreaming:642](../../backend/src/main/java/com/jobproof/modules/airesume/application/AiResumeWorkbenchService.java:642) 捕获后仍为原用户消息写入失败助手消息。
- **判断／后果：**第二个请求被拒绝时，原模型调用仍可能成功，并在 [同文件:892](../../backend/src/main/java/com/jobproof/modules/airesume/application/AiResumeWorkbenchService.java:892) 再插入成功助手消息。成功、失败都使用 attempt 1；[assistantFor:886](../../backend/src/main/java/com/jobproof/modules/airesume/application/AiResumeWorkbenchService.java:886) 只按 attempt_no 排序，后续重放可能拿到失败记录。消息唯一约束只覆盖 sequence/client id，见 [V21:80](../../backend/src/main/resources/db/migration/V21__ai_resume_workbench.sql:80)。
- **验证范围：**沿代码交错追踪，未对真实模型做双请求实验。应区分“请求未被接纳”与“已接纳尝试失败”，并确保同一尝试只提交一次终态。关联 T08。

**架构判断与保留方向：**不建议因为大文件就整体重写。当前 `AiResumeWorkbenchService` 约 1,752 行、`CareerPlanningService` 2,183 行、`AiResumeWorkbenchPage` 2,685 行，是需要优先定位变更责任的热点；职责交叠可从 [工作台保存与全量合并:696](../../frontend/src/features/ai-resume/pages/AiResumeWorkbenchPage.vue:696)、[生成与失败处理:538](../../backend/src/main/java/com/jobproof/modules/airesume/application/AiResumeWorkbenchService.java:538)、[职业规划依赖:125](../../backend/src/main/java/com/jobproof/modules/careerplanning/application/CareerPlanningService.java:125) 观察。已复现的保存缺陷证明一致性知识分散；尚未完成循环依赖图，不能声称存在或不存在所有循环依赖。建议先修不变量，再提取有真实复用价值的职责，见 T15/T16。

### 代码质量与用户数据正确性

#### F04 · 高 · 切换模板或确认另一张卡片会覆盖未保存内容

- **事实／位置：**[AiResumeWorkbenchPage.vue:727](../../frontend/src/features/ai-resume/pages/AiResumeWorkbenchPage.vue:727) 在模板切换后调用 `applyConversation(next, false)`；[同文件:707](../../frontend/src/features/ai-resume/pages/AiResumeWorkbenchPage.vue:707) 因此不再保护 dirty 卡片，而是覆盖所有 payload。确认卡片也在 [909 行](../../frontend/src/features/ai-resume/pages/AiResumeWorkbenchPage.vue:909) 使用相同调用。
- **复现：**前端代理在内存中运行实际函数：先修改姓名，在 1 秒保存防抖期间切换模板，输入从新值退回服务器旧值，dirty 状态却仍存在。尚未触发的保存还可能提交这个旧值；确认一张卡片也可能覆盖另一张脏卡片。
- **判断／后果：**视觉操作不应丢失用户编辑。应按操作范围合并：只更新服务器确认的卡片或版式，保留其他脏字段。关联 T05。

#### F05 · 高 · 迟到的保存响应破坏新编辑与本地恢复副本

- **事实 A／位置：**[设计保存:787](../../frontend/src/features/ai-resume/pages/AiResumeWorkbenchPage.vue:787) 发请求后允许继续编辑，响应在 795–800 行无条件覆盖 draft 并标记 saved。后续定时保存因 789 行的状态检查退出。
- **复现 A：**字体比例 1.1 保存中改为 1.2，旧请求返回后最终变回 1.1，界面显示 saved，实际只发生一次保存。PDF 导出调用该保存逻辑也不等于已经等待所有在途保存结束，见 [1554 行](../../frontend/src/features/ai-resume/pages/AiResumeWorkbenchPage.vue:1554)。
- **事实 B／位置：**[面试保存:163](../../frontend/src/features/mock-interview/pages/MockInterviewSessionPage.vue:163) 没有同题单次在途限制，任意成功响应在 170 行删除当前本地副本。
- **复现 B：**旧内容请求成功、新内容请求遇到 409，旧请求已删除新内容的备份；界面仍称“本地副本仍保留”。此处复现使用实际函数和可控制顺序的假网络响应，没有改写真实用户数据。
- **判断／后果：**这两处体现同一缺陷：没有将“服务器确认的保存”绑定到发出时的内容版本和对象身份。按最严重的面试数据丢失后果合并为高风险；两处应分别验收。关联 T06。

#### F10 · 中 · 面试评分把有效的零分过滤掉

- **事实／位置：**[MockInterviewAiService.java:129](../../backend/src/main/java/com/jobproof/modules/mockinterview/application/MockInterviewAiService.java:129) 接受包括 0 在内的评分；[MockInterviewService.java:505](../../backend/src/main/java/com/jobproof/modules/mockinterview/application/MockInterviewService.java:505) 汇总时只保留大于 0 的值，空集合使用 60 分。
- **判断／后果：**同一维度的 `[0,100]` 会得到 100 而非 50；全部为 0 时变成 60。这是可直接从算式确定的结果，不是 AI 主观质量评价。应该区分“缺失”和“有效零分”。
- **验证范围：**公式与输入约束检查；未修改既有报告数据。关联 T10。

### 测试与可发布性

#### F08 · 高 · 当前完整测试基线不通过，异步编辑的关键场景缺少保护

- **执行事实：**`mvn -B -o test` 运行 306 项，3 失败、0 错误、1 跳过，命令失败退出。不是沿用上周测试结果。
- **文件证据：**[AiResumeWorkbenchIT 报告:4](../../backend/target/surefire-reports/com.jobproof.AiResumeWorkbenchIT.txt:4) 有两项失败：预设期望 BLUE、实际 NO_PHOTO（6 行），设计版本期望 1、实际 0（18 行）；[SSE 报告:4](../../backend/target/surefire-reports/com.jobproof.modules.careerplanning.application.CareerPlanningSseServiceTest.txt:4) 的重放序号期望 `[2,3,4]`、实际 `[]`（6 行）。对应断言为 [AiResumeWorkbenchIT.java:320](../../backend/src/test/java/com/jobproof/AiResumeWorkbenchIT.java:320)、[383 行](../../backend/src/test/java/com/jobproof/AiResumeWorkbenchIT.java:383)、[CareerPlanningSseServiceTest.java:45](../../backend/src/test/java/com/jobproof/modules/careerplanning/application/CareerPlanningSseServiceTest.java:45)。
- **判断／后果：**目前不能把完整测试通过作为发版依据；在定位前也不能断言三个失败都是产品缺陷，可能包含默认模板变化、日期假设或测试隔离问题。修复应解释契约，不应简单改断言或删除测试。
- **测试空白证据：**[package.json:10](../../frontend/package.json:10) 主要运行提取出的工具逻辑；[mobile-layout.spec.ts:3](../../frontend/e2e/mobile-layout.spec.ts:3) 依赖已有求职者账户；[career-planning.spec.ts:44](../../frontend/e2e/career-planning.spec.ts:44) 创建账户并调用真实 API。未发现覆盖 F04/F05/F09 完整异步时序的组件用例，不能用当前 162 项前端测试通过替代它们。
- **关联：**T01、T05–T09、T13。没有覆盖率报告，因此不提供虚构的覆盖率百分比。

### 性能

#### F13 · 中 · 资料列表的行映射每条再查询引用数，形成 N+1

- **事实／位置：**[CareerLibraryService.java:152](../../backend/src/main/java/com/jobproof/modules/career/application/CareerLibraryService.java:152) 做总数查询及分页查询，分页 RowMapper 在 [553–556 行](../../backend/src/main/java/com/jobproof/modules/career/application/CareerLibraryService.java:553) 对每条结果额外查询一次引用计数。
- **判断／后果：**返回 N 条记录的这段列表读取需要 2+N 次 SQL，N=20 时就是 22 次；其他权限检查等查询不计入此数。远端数据库下会增加往返，且结果映射与查询耦合，使后续批处理也重复付费。
- **建议与范围：**分页取 ID 后批量 GROUP BY 引用数或单独聚合 JOIN，避免把 N+1 简单替换成多表乘积；未做线上延迟压测，不声称已有某个 p95。关联 T17。

其余性能维度审查较浅：没有测量 2 核 2G 机器的实际 RSS、并发导出峰值或磁盘增长，也没有完成全域索引和查询计划审查。Compose 的内存上限见 [docker-compose.yml:17](../../deploy/docker-compose.yml:17)、35、52、85、105 行；这些是上限而非实际占用，不能仅相加就宣称必然 OOM。容量验证见 T21。

### 依赖与构建

#### F14 · 中 · 两套锁文件不一致，缺少单一可重现构建约定

- **事实／位置：**npm 锁文件中的 Vite 为 8.2.1、vue-tsc 为 3.3.10，见 [package-lock.json:2785](../../frontend/package-lock.json:2785)、[3038 行](../../frontend/package-lock.json:3038)；pnpm 锁中为 8.2.2、3.3.11，见 [pnpm-lock.yaml:55](../../frontend/pnpm-lock.yaml:55)、62 行。[package.json:6](../../frontend/package.json:6) 没有选择 packageManager/engines，也没有 lint/format 命令；[pnpm-workspace.yaml:2](../../frontend/pnpm-workspace.yaml:2) 仍保留 `set this to true or false` 的构建授权占位。
- **判断／后果：**使用 npm 与 pnpm 会得到不同工具链；Docker 又直接复制预构建 JAR，没有在 [Dockerfile:16](../../backend/Dockerfile:16) 前证明它来自当前测试通过的源码。本机目录缺少 Git/CI 配置，见仓库地图中的扫描记录，不能确认已有外部发布门禁。
- **依赖查询事实：**本轮执行 `npm audit --package-lock-only --ignore-scripts --json --registry=https://registry.npmjs.org`，报告漏洞总数为 0。这只覆盖该 npm 锁及当时公告数据；未运行 Java 依赖 SCA、容器漏洞扫描或全传递依赖许可分析。
- **建议：**先按当前 README/npm 脚本选定 npm 和一份锁，固定 Node/JDK，使用不可变构建与测试证据；不因“版本看起来旧”就贸然全量升级。关联 T13/T14。

### 文档

#### F15 · 低 · 入门说明与当前代码存在具体偏差

- **事实／位置：**[frontend/README.md:3](../../frontend/README.md:3) 说 `/workspace` 跳转简历入口，实际 [router.ts:74](../../frontend/src/app/router.ts:74) 跳转资料库；[backend/README.md:49](../../backend/README.md:49) 给出的 Worker JAR 文件名与 [pom.xml:121](../../backend/pom.xml:121) 的 finalName 不一致，且该 classpath 用法未验证适用于 Spring Boot 可执行 JAR。README 61 行还称对象存储为本地 Spike，而 prod 已配置 MinIO。
- **判断／后果：**新开发者会被引向历史入口或无法运行的启动命令；AI 助手也容易沿旧文档继续建设已经退役的模块。
- **建议：**建立简短的当前运行说明，历史计划保持存档并明确时效；README 中的命令在干净环境执行一次。关联 T18。无需为此引入庞大的文档系统。

### 值得保留的优势

| 优势 | 证据与意义 |
| --- | --- |
| 确认内容与候选、版式分开 | [V21:24](../../backend/src/main/resources/db/migration/V21__ai_resume_workbench.sql:24) 保存独立修订、内容哈希；[工作台:896](../../frontend/src/features/ai-resume/pages/AiResumeWorkbenchPage.vue:896) 有显式确认流程。应修保存缺陷，保留这一产品模型。 |
| 会话基础防护存在 | [SecurityConfig.java:27](../../backend/src/main/java/com/jobproof/infrastructure/security/SecurityConfig.java:27) 使用 BCrypt；[SessionAuthFilter.java:56](../../backend/src/main/java/com/jobproof/infrastructure/security/SessionAuthFilter.java:56) 设置 HttpOnly/SameSite；[IdentityService.java:406](../../backend/src/main/java/com/jobproof/modules/identity/application/IdentityService.java:406) 在更换密码时撤销会话。 |
| 验证令牌有较强事务设计 | [ContactVerificationChallengeJpaRepository.java:23](../../backend/src/main/java/com/jobproof/modules/identity/infra/ContactVerificationChallengeJpaRepository.java:23) 使用悲观锁；[ContactVerificationService.java:182](../../backend/src/main/java/com/jobproof/modules/identity/application/ContactVerificationService.java:182) 将令牌绑定联系方式、用途与有效期后消费。可借鉴其并发纪律。 |
| 私有文件与扫描边界明确 | [FileAccessService.java:85](../../backend/src/main/java/com/jobproof/modules/storage/FileAccessService.java:85) 检查所有权和分享权限；[CareerFileService.java:151](../../backend/src/main/java/com/jobproof/modules/career/application/CareerFileService.java:151) 对扫描失败进行隔离。 |
| 已有可复用的正确保存模式 | [工作台卡片保存:863](../../frontend/src/features/ai-resume/pages/AiResumeWorkbenchPage.vue:863) 使用本地修订号和单次在途保护；[CareerPlanningPage.vue:382](../../frontend/src/features/career-planning/pages/CareerPlanningPage.vue:382) 比较提交时内容签名。F05 可以优先沿用项目自身模式。 |
| 测试并非全部空断言 | [mobile-layout.spec.ts:23](../../frontend/e2e/mobile-layout.spec.ts:23) 检查实际布局；[CareerPlanningSseServiceTest.java:44](../../backend/src/test/java/com/jobproof/modules/careerplanning/application/CareerPlanningSseServiceTest.java:44) 检查具体重放顺序，且本次确实捕获失败。 |
| 下载和导航有集中防护 | [client.ts:185](../../frontend/src/shared/api/client.ts:185) 验证下载响应；[safeNext.ts:26](../../frontend/src/features/identity/safeNext.ts:26) 限制回跳路径。没有确认的前端注入缺陷，但这不代表完成了 XSS 全量证明。 |
| 历史验收说明部分保持诚实 | [职业规划验收说明:19](../../docs/career-planning-visual-acceptance.md:19) 区分 NOT_RUN/FAILED/PASSED，33 行明确没有原图就不能宣称逐图通过。这一习惯应保留。 |

### 本轮实际执行记录

| 检查 | 结果 | 限制 |
| --- | --- | --- |
| `npm run test:logic` | 122 通过，0 失败 | 2026-09-05 本轮前段执行，恢复后未重跑。入口 [package.json:10](../../frontend/package.json:10)。 |
| `npm run test:components` | 12 文件、40 测试通过 | 输出存在 jsdom `Window.scrollTo()` 未实现提示，不能写“无警告”；配置见 [vitest.config.ts:12](../../frontend/vitest.config.ts:12)。 |
| `npm exec -- vue-tsc --noEmit -p tsconfig.app.json --incremental false` | 退出码 0 | 仅类型检查，不等于生产构建、浏览器验收或 PDF 输出通过。 |
| `npm audit --package-lock-only --ignore-scripts --json --registry=https://registry.npmjs.org` | 公告匹配漏洞总数 0 | 没有覆盖 Java、容器和全部许可证。 |
| `mvn -B -o test` | 306 项；3 失败；0 错误；1 跳过 | H2/test profile，本机 JDK 21；具体失败文件见 F08，未运行显式供应商 Probe。 |
| 前端异步函数受控复现 | F04、F05、F09 重现 | 使用真实源函数和假网络/媒体对象的内存执行；没有启动真实麦克风或宣称浏览器端到端通过。 |
| 生产构建、线上验收、Word/WPS、备份恢复、云端安全扫描 | 本轮未执行 | 审计期间不更换发布包、不改服务器；不得把历史部署成功当作本轮通过。 |

## 改进策略（阶段 3）

| 主题 | 目标状态与原则 | 关联证据 | 可衡量的完成信号 |
| --- | --- | --- | --- |
| 1. 用户编辑不会被迟到结果覆盖 | 每份草稿有对象身份、本地修订号、已确认版本；异步结果只确认发送时的快照，不能替用户取消之后的编辑。数据库用原子版本条件。 | F04–F06 | 慢响应、乱序、409、断网、模板切换、离页后恢复的回归全部通过；同版本并发写恰好一个成功，另一个明确冲突。 |
| 2. 明确一次请求与一次终态 | 请求接纳、执行失败、取消、重放分别建模；只有已获得执行权的尝试可以修改其终态和额度。 | F07/F08 | 同 requestId 双请求只产生一个有效助手终态、一次额度结算；拒绝和重放不改变原请求结果。 |
| 3. 生产秘密与恢复是交付的一部分 | 所有真实凭据从受控运行环境注入；传输加密；数据库与对象存储一起恢复。 | F01–F03/F11/F12 | 源码及构建包无真实秘密；HTTPS 登录正常且 Cookie Secure；独立恢复环境中数据库引用和文件 SHA-256 对账通过。 |
| 4. 用一套构建证据代替口头“测过” | 固定包管理器、Node/JDK 和测试入口，测试失败就阻止发布，记录构建来源和明确跳过项。 | F08/F14/F15 | 干净环境一条命令得到同一依赖图；类型、关键测试、lint、构建、秘密扫描任一失败均阻止发布；本次 3 项失败归零。 |
| 5. 围绕真实职责收敛复杂性 | 先隔离保存、生成尝试、列表查询，再逐步缩小页面和服务的修改范围；抽取必须减少调用方需要知道的规则。 | F05–F07/F13、架构热点 | 一处修正保存语义能覆盖两个真实调用方；资料分页查询量不随返回条数线性增长；核心业务测试继续通过。 |

### 不推荐现在做的事与权衡

- 不拆成微服务，不引入 Kubernetes、Kafka 或新的分布式数据库。当前是已有领域分包的单体且目标服务器资源有限，先处理已经有证据的数据与恢复缺陷；收益高于跨进程改造。依据：[backend/README.md:3](../../backend/README.md:3)、[Dockerfile:21](../../backend/Dockerfile:21)。
- 不以行数为目标全量拆分三份大文件，也不一次替换 JPA/JDBC。先通过失败用例定义行为，再在相同保存规则确实出现两次时抽取，避免增加转发层而没有减少复杂性。
- 不为本轮审计重做首页、十二套模板或已退役业务；没有本轮逐页视觉/导出证据，不能用架构判断要求全面返工。
- 不仅为了版本较新而全量升级依赖。先修锁文件和运行目标，再针对实际公告、支持状态和兼容测试安排升级；Java 与镜像漏洞状态目前未知。
- 不把全项目覆盖率 80% 当立即门槛。先覆盖具体丢稿、重复请求、非法访问和恢复场景，再收集覆盖率并对关键模块设阈值，避免大量低价值测试。
- 不删除 HICV 或参考项目资产。当前许可文本涉及来源保留及非商业分发，[LICENSE:5](../../简历模板/hicv-word-resume-templates-main/hicv-word-resume-templates-main/LICENSE:5)；其商业使用范围须由产品方确认，本报告不提供法律结论。

### “升级协作方式”的具体落点

这里的“升级自己”指改进后续任务的执行与验收方法，不代表可以修改模型权重。本项目可以建立一页当前项目指南：有效路径、模块负责人、公开接口、执行命令、变更的证据要求。历史多代理约定已经强调交接证据和避免同文件脏合，见 [协作约定:135](../../docs/plan/JobProof-AI-P0A-多智能体协作约定.md:135)，但它同时包含旧产品阶段，应提炼当前有效部分。

建议所有后续代理任务只记录四项：负责的文件/问题、已经验证的证据、禁止触碰的领域、停止时间；主代理统一管理问题编号和执行中的测试，防止重复扫描、重复跑整套测试及“代码存在就称完成”。对应 T18；本轮没有更改 AGENTS、技能、记忆或全局配置。

## 任务计划（阶段 4）

估算为熟悉现有工程的一名工程师的投入：**S <2 小时，M 半天，L 1–2 天，XL 必须继续拆分**。云凭据授权、域名配置和产品决策等待时间不包含在开发估算中。风险列指实施变更本身可能造成的破坏，不是发现的严重性。以下均为待执行任务，不是已修复结果。

### 里程碑 0：建立安全网

| ID / 标题 | 描述与受影响区域 | 验收标准 | 努力 / 变更风险 | 依赖 |
| --- | --- | --- | --- | --- |
| T01 固定基线并解释 3 个失败 | 为当前源码建立受控版本/文件哈希记录，固定 Java 17 与 Node 目标，定位 F08 的三项失败；契约错误修业务、过期假设修测试，并留下原因。区域：`backend/pom.xml`、F08 两个测试类、`frontend/package.json`、未来 CI。 | 干净环境复现旧失败；修复后三个用例与完整后端测试通过；不以删断言、无理由 skip 通过；保存测试报告与源码标识。 | M / 中 | 无；确认现有权威 Git 仓库位置，不覆盖现有目录 |
| T02 完整备份与失败恢复 | 重构 `deploy/backup.sh`，覆盖数据库、MinIO 对象及必要恢复配置；加入退出清理及原始运行状态检查，在独立环境完成恢复。 | 模拟 tar 失败后服务恢复原状态；成功备份能还原一份资料、一份模板和一份 PDF 的引用及哈希；明确保留期与 RPO/RTO。 | L / 高 | 恢复演练目录、加密密钥的独立保管位置 |

### 里程碑 1：关键修复

| ID / 标题 | 描述与受影响区域 | 验收标准 | 努力 / 变更风险 | 依赖 |
| --- | --- | --- | --- | --- |
| T03 移出并轮换真实凭据 | 去掉 `DevVerificationProviderConfiguration` 的真实值，改为环境注入，测试使用假供应商；同步完成 SMTP 与云密钥轮换，检查构建包和既有分发物。 | 源码/发布包扫描无真实凭据；旧值失效；新值通过受控邮箱/手机验收；失败时日志无秘密。 | M / 高：可能短时影响验证码 | 凭据管理权限；可与 T01/T02 并行，不等待安全重构 |
| T04 完成 HTTPS 入口 | 为 `deploy/nginx.conf`、Compose、prod origin/cookie 配置加入 TLS 与续期路径，维护登录跳转和 SSE。 | HTTP 跳 HTTPS；证书与域名一致；登录 Cookie Secure；验证码确认、下载与 SSE 可用。 | M / 高：错误配置可阻断登录 | 域名或既有 TLS 入口信息 |
| T05 保留跨模板/跨卡片的脏内容 | 在 AI 工作台合并会话响应时保留非目标卡片的新修订，模板变更只更新版式；避免简单把所有操作都强制全量覆盖。 | 防抖期改姓名→换模板→刷新，值不丢；确认 A 时 B 的未保存编辑保留；内容哈希不因换版被改写。 | S / 中 | T01；先增加对应失败场景 |
| T06 串行化设计和面试草稿保存 | 为两处保存增加对象键、请求快照、本地修订与单次在途控制；只有确认相同快照后才能移除本地副本；导出显式等待保存排空。 | 延迟旧响应不覆盖新输入；409/断网后新副本存在；重进页面可恢复；PDF 请求在最后设计确认后发送。 | M / 中 | T01/T05；覆盖 F05 两个独立调用方 |
| T07 使用真正的乐观锁 | 资料主档、记录和面试答案更新在 SQL 条件中比较版本并检查更新行数；事件版本来自成功写入结果。 | 同版本双线程提交只有一次成功；另一请求 409；不写错误快照事件；覆盖同账号与越权两种情况。 | M / 中 | T01；若新增约束，只加新迁移 |
| T08 修复生成尝试接纳与终态 | 在 `AiResumeWorkbenchService` 与 `AiGenerationAttemptService` 区分接纳拒绝和执行失败，明确终态提交者及重放结果。 | 相同请求并发、断线重试、取消各产生合法唯一终态；拒绝不插助手失败；结算最多一次；重放顺序稳定。 | M / 中 | T01；若需数据约束依赖历史重复项评估 |
| T09 关闭迟到的录音流 | 在面试组件加入 disposed/代次检查，卸载后返回的媒体流立即停止，不创建计时器、录音器或上传任务。 | 延迟授权到页面卸载之后，全部轨道停止、录音启动数和上传数为零；正常录音仍能结束。 | S / 低 | T01 对应组件回归 |
| T10 修复零分与缺失分数 | 去掉汇总中对零分的过滤，明确缺失维度与未评分状态，统一评分样本集合。 | `[0,100]→50`、全零→0；缺失字段不会悄悄记为60；已有报告不无提示批量重算。 | S / 低 | 产品确认缺失评分展示口径 |
| T11 解析可信来源 IP | 配置明确的可信代理来源、真实 IP 和 scheme 解析；验证不能靠伪造请求头绕过频控。 | 两个原始 IP 互不占用限额；同 IP 上限生效；直连伪造 XFF 无效；日志仅留脱敏结果。 | M / 中 | T04 的代理拓扑可以共用，但修复不必等证书 |
| T12 增加密码登录节流 | 在身份服务加入账号+来源双维度限制、短时退避和失败审计，错误信息不泄露账户存在性。 | 重复失败受限；正常用户恢复后可登录；分布式计数共享；不会因攻击导致账号永久锁定。 | M / 中 | T11；Redis 故障口径明确 |

### 里程碑 2：高杠杆改进

| ID / 标题 | 描述与受影响区域 | 验收标准 | 努力 / 变更风险 | 依赖 |
| --- | --- | --- | --- | --- |
| T13 固化持续检查与发布来源 | 新增轻量 CI：依赖锁校验、类型、行为测试、渐进 lint、秘密扫描和构建；前后端包关联同一源码标识，区分假供应商回归与真实供应商验收。 | 任一检查失败不产正式发布；构建包可追溯；无外部真实 AI 凭据也能跑核心回归；重大跳过项有理由。 | M / 低到中 | T01/T14、权威仓库位置 |
| T14 统一包管理器与运行目标 | 以当前 npm 命令为默认候选，明确团队选择后只维护一套锁；固定 Node/JDK 与包管理器，处理 pnpm 授权占位。 | 干净安装产生同一依赖图；CI 和本地命令一致；不重新解析另一套锁发布；无需个人磁盘路径。 | S / 低 | 团队未有相反规范；删除多余锁前保存基线 |
| T15 提取有两个调用方的草稿保存模块 | 在 T06 行为稳定后，把保存队列、修订比较、失败恢复和离页清理封装为一个小接口；先接设计与面试，避免为全站表单一次造通用框架。 | 两个调用方共享行为测试；页面只传身份、快照和保存适配；既有卡片的确认流程不被混入自动保存。 | L / 中 | T05/T06/T09 |
| T16 收拢生成尝试职责 | 把已稳定的请求接纳、完成、取消和重放逻辑从工作台编排中抽离，模型适配与内容确认继续保持各自职责。 | 工作台不再自行拼出尝试终态 SQL；重复请求和额度测试保持通过；公开 API 不变化。 | L / 中 | T08 |
| T17 消除资料列表 N+1 | 把 `CareerLibraryService` 的行映射改为纯映射，以分页 ID 批量查询引用数或使用聚合子查询。 | 1、20、100 条列表的引用计数准确，SQL 次数保持常数上限（建议总计≤3次，不含统一权限检查）；排序分页不变。 | S / 低 | T01 资料列表回归；实测查询计数 |

### 里程碑 3：质量与润色

| ID / 标题 | 描述与受影响区域 | 验收标准 | 努力 / 变更风险 | 依赖 |
| --- | --- | --- | --- | --- |
| T18 更新当前说明与协作索引 | 修两份 README 的入口、JAR 和存储说明，提供当前项目根路径、测试/部署命令及历史文档索引；提炼简短的代理分工/证据规范。 | 新环境按说明能启动；链接可达；历史计划有日期与非当前声明；不会把测试通过写成上线验收通过。 | S / 低 | T01/T13 的命令与目录约定 |
| T19 做一次依赖与资产许可台账 | 补 Java/镜像 SCA 和运行时依赖清单；记录 HICV 的来源、许可与商业模式开关，按实际结论安排升级或授权。 | 所有运行依赖有版本/来源；可达高危公告有处置结论；资产发布模式与确认的用途相符。 | M / 低 | 商业用途确认；扫描服务可用 |
| T20 决定通用签名下载 API 去留 | 先确认 `/api/v1/files/{id}/download-url` 是否仍有消费者；使用则配外部签名 endpoint 或鉴权代理，不用则受控弃用。 | 使用场景下容器外客户端能下载且越权失败；不用时无调用残留与死入口，不能只改成公开桶。 | M / 中 | 产品/API 消费者确认，见开放问题 4 |
| T21 建立适配 2 核 2G 的容量证据 | 对登录、资料列表、单份 PDF 和文件预览分别记录 RSS、队列、延迟和失败；按测量限制并发，添加磁盘/内存/任务失败告警。 | 可复现基准和资源预算；峰值不会把同步登录拖死；扫描失败仍关闭；性能目标由产品方确认。 | M / 低 | T02/T13/T17；可用测试环境 |

### 前三个任务的实现草图

**T01：先建立可解释基线。** 记录源码清单与敏感文件排除规则，确认权威 Git 仓库后建立对应版本；在和 Docker 一致的 JDK 17、固定 Node 上运行现有命令。分别查看两个模板测试是否硬编码了已变化的默认模板，以及 SSE 固定日期与保留期/重放条件的关系——这些是待验证假设，不是已认定根因。修复必须保存“为什么断言应当成立”的证据；陷阱是直接将 BLUE 改成 NO_PHOTO 或给 SSE 测试加 skip，以掩盖契约不一致。

**T02：恢复的是一个系统快照。** 先列出数据库、MinIO、legacy 文件和加密密钥的依赖，定义允许的维护窗口；暂停写入或采用一致快照，分别导出数据库与对象清单并生成哈希清单，独立加密保管恢复所需配置。脚本用退出处理恢复进入脚本前的服务状态，保留失败归档不冒充成功；最后在独立网络/目录中恢复并验证引用到文件的闭环。陷阱包括 tar 运行失败导致服务停机、备份还在同一磁盘、只恢复数据库以及误把原本停止的服务启动。

**T03：改代码与凭据生命周期一起完成。** 将真实供应商字段改成必需环境配置，测试通过假适配器运行，建立秘密扫描；建立新凭据后注入受控运行环境，验收一封邮件和一条测试短信，再撤销旧值并确认旧值不再可用。若已确认泄露或滥用，应先撤销而非等待平滑切换。扫描范围包含源码、编译产物与已知分发包，但日志只记录掩码和布尔校验结果；陷阱是只删源码而旧 JAR 或旧云密钥继续有效。本轮只提出该方案，未执行轮换。

### 快速获胜：高回报且 S 努力

| 任务 | 为什么值得先做 | 完成证据 |
| --- | --- | --- |
| T05 跨模板保留脏内容 | 直接阻止用户已输入简历内容被丢弃 | 防抖期间编辑→切换→保存→刷新断言 |
| T09 迟到媒体流清理 | 小范围生命周期修正，避免离页后录音 | 延迟授权卸载测试：轨道已停止，零上传 |
| T10 零分汇总 | 确定性算式错误，定位清楚 | 零分、混合分数、缺失分数样例 |
| T14 单一锁文件约定 | 降低每次开发和发布的环境差异 | 干净安装与 CI 使用同一锁 |
| T17 批量引用计数 | 消除明确的按条增长查询 | 不同页大小的 SQL 计数 |
| T18 修当前入口文档 | 降低人和代理重复探路的成本 | 新环境按说明执行、链接检查 |

## 开放问题与暂停状态

1. **生产入口：**权威域名是什么，服务器之外是否已经有负载均衡/WAF/TLS 终止？答案影响 T04/T11；本次只审了本地部署文件，未核实云控制台。
2. **凭据：**谁有权限轮换 SMTP 与 RAM 密钥，旧源码/构建包分发过哪些地方？本次知道源码与部署值相同，不知道供应商侧当前状态。
3. **数据恢复目标：**最多允许丢失多久的数据（RPO）、停机多久（RTO），对象文件保留多久？建议先提出适合当前规模的目标再执行 T02，不把昂贵高可用作为默认要求。
4. **签名链接是否仍有消费者：**[MinioObjectStorage.java:41](../../backend/src/main/java/com/jobproof/modules/storage/MinioObjectStorage.java:41) 用同一内部 endpoint 签名，97–103 行返回该 URL；[docker-compose.yml:65](../../deploy/docker-compose.yml:65) 固定 `http://minio:9000`；[FileAccessService.java:85](../../backend/src/main/java/com/jobproof/modules/storage/FileAccessService.java:85) 暴露该能力。容器网络外无法直接使用这个地址，但未发现已确认的前端消费者，所以没有把它计成“整个下载功能坏了”的正式高风险；应决定修复或弃用。
5. **部署容量：**2 核 2G 下希望支持多少同时在线用户、多少并发 AI/PDF/扫描任务？没有负载数据前，不承诺具体容量，也不直接要求扩容。
6. **产品口径：**模拟面试缺失评分应显示“未评分”还是排除该维度？历史已生成报告是否重算？零分与缺失必须区分，历史改写需要单独决策。
7. **模板商业使用：**是否仍是非商业模板分发；如果将来收费，HICV 资产的授权与隐藏策略由谁确认？依据是本地 LICENSE，非法律意见。
8. **权威仓库与协作：**本地目录没有 `.git`；是否存在应接入的远程仓库与现有 CI？本轮不擅自初始化、推送或覆盖任何仓库。
9. **未验证项目：**Java/镜像已知 CVE、整个系统覆盖率、MySQL 空库/升级迁移、所有 PDF 模板分页一致性、实际手机媒体行为与完整备份恢复均未完成验证，不能据本文判定健康。

**交付后暂停。** 阶段 1–4 的审计文档已给出，代码修复与生产操作均未开始；下一轮可直接按 T01/T02/T03 安排分工，无需重复本轮目录扫描和已经确认的缺陷查找。没有活跃子代理或后台审计任务继续执行。
