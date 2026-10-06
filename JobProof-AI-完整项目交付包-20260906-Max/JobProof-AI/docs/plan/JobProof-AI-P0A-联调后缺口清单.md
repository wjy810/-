# JobProof AI P0A 联调后缺口清单

> 审计日期：2026-08-18（复审）
> 角色：P0A 缺口清单复审官（只读）
> 对照源：`开发文档/JobProof-AI-P3.5-业务逻辑确认记录-v0.1.md`（已冻结 v0.7）
> 辅读：`docs/plan/JobProof-AI-P0A-后端实施计划.md`、`docs/plan/JobProof-AI-P0A-前端实施计划.md`、S0–S5 前后端交付说明、`docs/plan/JobProof-AI-P0A-数据权利前端交付说明.md`、迭代日志最近已交项
> 取证方式：按契约条目搜索 `backend/`、`frontend/src` 的模块、Controller、路由、页面；不凭交付说明断有无
> **本折只更新本文。未改 `backend/`、`frontend/` 任何代码。未追加 `自主迭代日志`。**

本清单只收 **P0A 已冻结必做项** 的有/无。P0B/P1/P2 愿望清单不升格为 blocker。

---

## 0. 计数与下一刀

| 分级 | 条数 |
| --- | --- |
| `P0A-blocker` | **0** |
| `P0A-gap` | **0** |
| `已交付` | **14** |
| `明确不做（P0B+）` | **8** |

**仍开的 P0A 项：无。** 原 blocker / gap 均已按代码与交付说明收口。建议下一刀：**P0A 端到端复冒烟验收**（不改契约、不开 P0B）。

---

## 1. 条目明细

每条格式：契约出处 → 代码证据 → 分级 → 建议下一刀。

### 1.1 数据权利导出 / 删除最小页

**契约出处**

- 确认记录 **12.4.15–18**：本人可发起导出；导出异步、下载限时；对象级或账号级彻底删除走数据权利；提交前展示关联影响、不可逆说明和法定例外入口。
- 确认记录 **12.1.4 / 13.1 账号**：注销不立即物理删除，进入删除编排。
- 确认记录 **13.2 数据权利**：已确认操作 = 导出、对象/账号删除申请；禁止隐藏不可逆影响。
- 前端实施计划 **3.2 / 3.4**：P0A 工作区必须含「数据权利」，对应 S0。

**代码证据（已交付）**

- **后端 API**：`backend/src/main/java/com/jobproof/modules/datarights/web/DataRightsController.java`
  - `GET /api/v1/data-rights/deletions/preview`
  - `POST /api/v1/data-rights/deletions`
  - `GET /api/v1/data-rights/deletions/{id}`
  - `POST /api/v1/data-rights/exports`
  - `GET /api/v1/data-rights/exports/{id}`、`.../cancel`、`.../retry`、`.../download`
- **前端工作区**：`frontend/src/app/router.ts` 路由 `/account/data-rights` → `DataRightsPage.vue`
- **Feature 与客户端**：`frontend/src/features/datarights/`（`dataRightsApi.ts`、`types.ts`、`labels.ts`）
- **入口**：`AppChrome.vue` 顶栏「数据权利」；`AccountPage.vue` 链接；`workspaceNav.ts` 把 `ACCOUNT_EXPORT` / `ACCOUNT_DELETION` 链到 `/account/data-rights?taskId=…`；`notification/labels.ts` 导出/删除进度链到 `?exportId=` / `?deletionId=`
- **账号注销**：`DataRightsPage.vue` 二次确认后 `POST …/deletions` `scope=ACCOUNT`
- **对象级**：同页 `scope=OBJECT` 预览/提交，消费 `impacts` / `canProceed` / `blockers`（见 1.2）
- **导出限时下载**：`downloadAvailable` → `GET …/exports/{id}/download`；`dataRightsApi.ts` 验 JSON 魔术字节，拒绝 HTML 假下载

**分级：** `已交付`（2026-08-18 数据权利前端官 + 点验官 + 下载回归官）

**建议下一刀：** 无需。不要补分享 UI（见 1.11）。

---

### 1.2 对象级删除编排与关联影响预览

**契约出处**

- 确认记录 **7.1.7**：彻底删除走数据删除流程，提交前展示已绑定投递和已冻结版本影响。
- 确认记录 **7.3.25**：提交前展示面试、复盘、简历版本绑定影响。
- 确认记录 **12.4.17 / 13.2**：对象级或账号级彻底删除；提交前展示关联影响。
- 确认记录 **13.4**：删除请求状态含已提交、处理中、部分受限、已完成、失败。

**代码证据（已交付）**

- `DataRightsService.previewDeletion()`：`scope=OBJECT` 时调 `ObjectDeletionCatalog.analyze()`，返回真实 `impacts[]` / `canProceed` / `blockers`；`ACCOUNT` 仍返回固定 IMPACT / LEGAL_NOTE（`backend/.../DataRightsService.java` 第 110–140 行）
- `DeletionOrchestrator.processOne()`：读 `request.getScope()`；`OBJECT` 走 `processObject()`，**不再** `markDeletionPending` / 撤全部分享 / 删全部私有文件 / 账号级 `onAccountDeletion`（`backend/.../DeletionOrchestrator.java` 第 102–108 行）
- `ObjectDeletionCatalog.java`：按目标类型汇总投递、冻结版本、面试、复盘、证据引用等关联；有效引用证据提交 409 `EVIDENCE_REFERENCED`
- 集成测试：`backend/src/test/java/com/jobproof/ObjectDeletionIT.java`（预览清单、投递对象删除、主档级联、证据引用闸、他人 403）
- 前端：`DataRightsPage.vue` + `labels.ts` `objectSubmitBlockedReason` 仅在 `scope=OBJECT` 且 `canProceed=true` 且无 BLOCKING 时开放提交

**分级：** `已交付`（2026-08-18 对象级删除后端官 + 数据权利页点验官）

**建议下一刀：** 无需。Controller 仍无 `GET /deletions` / `GET /exports` 列表——冻结契约未要求，不升格 gap。

---

### 1.3 证据类型是否齐

**契约出处**

- 确认记录 **12.3.11 / 13.2 证据库**：P0A 六类 = 项目经历、实习/工作成果、作品/链接、证书、课程/竞赛奖项、文字自述。情景测评、导师书面反馈不进 P0A。
- 确认记录 **12.3.13 / 13.6 MOCK-EVIDENCE-001**：被有效冻结版本引用后改正文拒绝，须复制新证据；只能归档。

**代码证据**

- 后端 `EvidenceType`：`PROJECT`、`INTERNSHIP_OR_WORK`、`PORTFOLIO`、`CERTIFICATE`、`COURSE_OR_CONTEST`、`TEXT_STATEMENT`；未知类型抛 `EVIDENCE_TYPE_UNKNOWN`。
- 前端 `evidence/types.ts` + `labels.ts` 的 `EVIDENCE_TYPES` 与上述六类一一对应。
- 列表/详情页可按六类创建、归档、恢复、复制；已引用时 `bodyMutable=false`，文案要求复制新证据；**没有物理删除按钮**（与 12.3 / 13.2 一致）。
- Controller：`GET/POST /api/v1/evidences`、`PUT /{id}`、`/{id}/archive|restore|copy`、`/{id}/reference-check`。

**分级：** `已交付`

**建议下一刀：** 无需。不要补情景测评 / 导师书面反馈。

---

### 1.4 简历冻结前置

**契约出处**

- 确认记录 **7.2.10–11 / 10.2 / 10.7 MOCK-RESUME-002/003**：只有主档 `可投递` 才能冻结；进入可投递和冻结前须同时满足：无未确认 AI 事实；每条关键成果要么关联允许使用的证据，要么逐条确认「暂无证据仍要冻结」。

**代码证据**

- 后端 `ResumeFreezePolicy.assertReadyAndResolved` / `assertCanMarkReady`：拦截未确认 AI（`UNCONFIRMED_AI_FACTS`）、未处理成果（`OUTCOME_EVIDENCE_REQUIRED`）、主档非 `READY_TO_APPLY`。
- `ResumeController`：`POST /{id}/ready`、`/{id}/freeze`、`/{id}/outcomes/{outcomeId}/waive`、`/{id}/outcomes/{outcomeId}/evidence`。
- 前端 `ResumeEditorPage.vue`：未处理成果计数、逐条勾选确认、`waiveOutcome`、标可投递与冻结按钮按 `readyBlockReason` / `freezeBlockReason` 禁用；列表页写明无物理删除。
- 创建投递 `ApplicationService.create` 必须 `requireBindable` 简历版本 + `requireConfirmedForBinding` 岗位；前端 `ApplicationCreatePage.vue` 下拉只收 `CONFIRMED` 岗位，并写明须已冻结/已绑定版本。

**分级：** `已交付`

**建议下一刀：** 无需。

---

### 1.5 匹配「无法判断」

**契约出处**

- 确认记录 **4.2.7 / 4.3.13**：等级含 `无法判断`。**仅当**求职方向缺失，或 7 个正式匹配确认字段中有 4 个及以上为未知、无法形成有效对照时，整份报告为 `无法判断`。不得因单个未知字段把整份打成 `无法判断`。加分不能改变该等级。
- 确认记录 **4.6.28**：匹配失败、取消或 `无法判断` 都不得自动创建投递。
- 确认记录 **13.6 MOCK-PROFILE-002**：无求职方向匹配拦截。

**代码证据**

- 正式路径用 `MatchScoringEngine`（`MatchingService.completeMatch` 调用），不是闲置的 `MatchingScorer`。
- `unknownFormalFieldCount()` 数岗位名称、职责、硬性技能、经验、学历、地点、工作方式 7 项。
- `MatchScoringEngine` 第 76–79 行：`unable = !directionConfirmed || unknownJobFields >= 4`；**已删**第三条件 `noJudgedDimension`；命中 4.3.13 时 `total = null`（第 83–85 行）。
- `MatchingService.start` 先 `profileService.assertDirectionConfirmed`；前端岗位确认页 / 报告页捕获 `DIRECTION_NOT_CONFIRMED`。
- 报告页 `unableToJudge` 或 `grade === '无法判断'` 时不把总分当已判定等级；`MatchingService` 无 `prepare`/`record` 调用。
- 单测：`MatchScoringEngineTest`、`JobMatchingContractIT.unableToJudgeReportOmitsFabricatedTotalAndDoesNotCreateApplication`。

**分级：** `已交付`（含原 1.5b 第三触发条件收口，2026-08-18 匹配无法判断核修官）

**建议下一刀：** 无需。

---

### 1.6 面试不回写投递

**契约出处**

- 确认记录 **7.3.27 / 11.1.6 / 11.4.28 / 13.1 / 13.5 / 13.6 MOCK-INTERVIEW-002**：轮次结果、轮次完成不得改投递主状态。进入 `面试中` 不强制建轮次。

**代码证据**

- `InterviewService` 只 `requireOwnRef` / `requireWritableApplication`（校验归属与归档），**没有**调用 `ApplicationService.advance/correct/withdraw`。
- 写轮次后的审计文案写明「不改投递主状态」；`update` 改 `result` 只写轮次实体。
- 前端面试列表/详情只读展示 `applicationStatus`，前进/更正/填结果后的提示均为「不改投递主状态」；投递详情另有独立阶段按钮，互不自动触发建轮次。

**分级：** `已交付`

**建议下一刀：** 无需。

---

### 1.7 通知失败语义

**契约出处**

- 确认记录 **11.3.21–22 / 13.5 / 13.6 MOCK-NOTIFY-001/002**：站内写入成功即 `已送达`；同一事件 ID+类型+接收人去重；通知失败不回滚业务。
- 确认记录 **11.3.20**：P0A 类型只许任务完成/失败、面试提醒、数据导出/删除进度、安全与数据权利。不做 Offer 截止。邮件短信属 P0B。
- 确认记录 **10.5 模块交互**：Application CRM → Notification，阶段变化，异步最终一致，通知失败不回滚投递。

**代码证据**

- `NotificationService.request` / `upsertActive`：`REQUIRES_NEW` 写送达；失败另开事务写 `SEND_FAILED` 或返回合成失败视图，**不抛给业务调用方**。
- `InterviewService.syncReminder`：无时间或非 `SCHEDULED` 则 `expire`，不建确定提醒；提醒失败只打日志。
- `DataRightsService.tryNotify`、`DeletionOrchestrator` 通知失败不改删除状态。
- 前端通知页展示 `SEND_FAILED`，禁止把失败标成已读；安全/数据权利通知标为不可关闭。
- **投递阶段通知**：`NotificationType.APPLICATION_STAGE` + `ApplicationService.notifyStage`（第 337–341 行）。相对 11.3.20 白名单为扩展类型，但 **10.5 要求阶段变化通知**；迭代日志核过保留，不删以免伤 CRM 联调。前端 `notification/labels.ts` 已翻译。

**分级：** 失败不回滚 / 去重 / 已读 / 阶段变化通知 **`已交付`**（2026-08-18 核过保留）

**建议下一刀：** 无需。不要借此开偏好中心或 P0B 通知中心。

---

### 1.8 账号与会话

**契约出处**

- 确认记录 **12.1 / 13.2 登录 / 13.6 MOCK-AUTH-001**：唯一登录邮箱+密码；验证码重置；服务端会话 + HttpOnly Cookie；退出当前会话；改密后全部会话失效。手机号、第三方、MFA 不进 P0A。

**代码证据**

- 后端 `AuthController`：`/auth/register|login|logout`、`/auth/password/change`、`/auth/password/reset/request|confirm`、`GET /me`。
- `SessionAuthFilter.writeSessionCookie`：`httpOnly(true)`，Cookie 名 `jobproof_session`。
- `IdentityService.changePassword` / `confirmPasswordReset` 走 `revokeAllByAccountId`；`logout` 只撤销当前会话。
- 前端：`/login` `/register` `/reset` `/account`；`authApi.ts` 对接上述接口；`credentials: 'include'`；顶栏退出；改密须勾选「全部会话立即失效」。
- 登录页与 `AuthStage` 写明不做手机号、第三方、MFA。源码无 oauth/mfa/手机号登录表单。
- **注销入口**：并入数据权利页账号删除/注销（见 1.1），不在登录页单独做按钮——与契约一致。

**分级：** `已交付`

**建议下一刀：** 无需。不要补手机号/第三方/MFA。

---

### 1.9 画像字段与匹配前门

**契约出处**

- 确认记录 **12.2 / 13.2 画像**：匹配前必填求职方向；建议补六项缺失保持未知；可后补技能/年限/兴趣/薪资预期/自我介绍；不采集敏感属性；完整度规则。

**代码证据**

- 后端 `ProfileFactKey` 覆盖方向、建议六项、技能、年限、兴趣、薪资预期、自我介绍；`ForbiddenProfileAttributes` 拒绝性别/年龄/民族/婚育/证件等。
- `ProfileController`：读写事实、候选确认/拒绝、`/completeness`、`POST /matching-gate`。
- 前端 `ProfilePage.vue` + `labels.ts` `SCALAR_CATALOG` 分组与契约一致；探测匹配前门；不提供敏感字段表单。

**分级：** `已交付`

**建议下一刀：** 无需。

---

### 1.10 简历 PDF 导出（发起 + 限时下载）

**契约出处**

- 确认记录 **7.2.14 / 10.2 简历编辑器**：P0A 可从 `已冻结` 或 `已绑定投递` 版本导出 PDF。

**代码证据（已交付）**

- 后端 `POST /api/v1/resumes/versions/{versionId}/export-pdf`；`ResumeService` 渲染 PDF 后 `storePdf`，成功结果 JSON 含 `fileId`。
- `TaskView`（`backend/.../TaskView.java`）：`SUCCEEDED` 且 payload 含 `fileId` 时回传 `fileId`、`downloadAvailable=true`、`downloadUrl=/api/v1/files/{fileId}/download`。
- `GET /api/v1/files/{id}/download`：本人会话取 PDF 字节；他人 403 `OBJECT_FORBIDDEN`。
- 前端 `ResumeEditorPage.vue`：冻结/已绑定可导出；成功后「下载 PDF」走 `downloadPrivateFile` / 会话 `downloadUrl`；无 `fileId` 时禁用并说明，不假下载。
- 测试：`TaskViewDownloadFieldsTest`、`ResumePdfExportIT`。

**分级：** `已交付`（2026-08-18 简历 PDF 下载官 + 点验官）

**建议下一刀：** 无需。不要做分享站外永久裸链。

---

### 1.11 分享底座

**契约出处**

- 确认记录 **7.2.14**：分享走数据权利，默认只读、限时、可撤销。
- 确认记录 **13.2 数据权利** 已确认操作只写了导出与删除申请，**没有**把「创建分享」列成页面必做按钮。
- 后端实施计划 S0：分享底座要接通。

**代码证据**

- 后端 `POST /api/v1/data-rights/shares`、`POST /shares/{id}/revoke` 已实现（S0 底座，删除编排会撤销）。
- 前端无分享入口、无 API 客户端。简历页明确「这不是分享链接」。数据权利页已确认操作只有导出与删除申请。

**分级：** `明确不做（P0B+）`

**建议下一刀：** 不要做。不要补创建/撤销分享 UI，不要做公开广场、邮件短信、导师协作。

**核过（2026-08-18 · 分享入口核修官）：** 核过：P0B，不实现。冻结契约 **13.2** 数据权利已确认操作仅「导出、对象/账号删除申请」；**12.4** 标题为「导出与删除最小集」；第 5 轮「运营、权限细化、数据权利细则」未开始且不挡 P0A。**7.2.14** 只约束「若分享则走数据权利，默认只读、限时、可撤销」，不是 P0A 页面必做按钮。架构写明导师协作不进 P0A。本刀未写功能代码。

---

### 1.12 复盘确认弱项、不改投递

**契约出处**

- 确认记录 **11.2.15–17 / 13.6 MOCK-REVIEW-001–003**：只有确认项写入已确认改进项；拒绝/未处理不写；复盘不得改投递/轮次/正式技能等级。

**代码证据**

- `ReviewService.confirmSuggestion` 调 `profileService.appendConfirmedImprovement`；`rejectSuggestion` 不写画像；`complete` 不把未处理项写入。
- 无投递阶段写入。前端复盘页逐条确认/拒绝、完成前二次确认未处理不写入。

**分级：** `已交付`

**建议下一刀：** 无需。

---

### 1.13 JD 七项确认、STALE 对比、重复岗位

**契约出处**

- 确认记录 **4.1.1 / 4.5.22 / 4.5.25**：正式匹配前至少确认七项；全文相同外不自动合并；STALE 可查看、可对比、可主动归档；无报告物理删除。

**代码证据**

- 前端 `JobConfirmPage.vue` 七项表单 + 未知戳 + 可能重复勾选并入；STALE 列表可归档、无物理删除。
- 报告页可与另一份对比。`MatchingController` 有 `GET /reports/{id}/compare`、`POST /reports/{id}/archive`。

**分级：** `已交付`

**建议下一刀：** 无需。

---

### 1.14 工作台只读汇总

**契约出处**

- 确认记录未冻结卡片；前端计划 **3.3**、后端计划 **4.6**：只读十块，写入打回领域页。

**代码证据**

- `WorkspaceController` `GET /api/v1/workspace` 与 `/workspace`。
- 前端 `/workspace` 只读；`workspaceNav.ts` 把领域 href 映射到已有页；`ACCOUNT_EXPORT` / `ACCOUNT_DELETION` 已链到数据权利页（见 1.1）。无漏斗/收藏/推荐入口。

**分级：** `已交付`

**建议下一刀：** 无需。

---

### 1.15 匹配报告空分母展示 helper

**契约出处**

- 确认记录 **4.3.13 / 4.4.17 / 4.4.18**：4.3.13 无法判断仅两条；无总分不能映射高度/部分/缺口；加分不能改变无法判断等级。
- 引擎边案：方向已确认、七项未知不足 4、对照项全未知时 `unableToJudge=false` 但 `total=null`，等级字段仍可能为「无法判断」——前端不得渲染人造 0 分或高度匹配绿印。

**代码证据（已交付）**

- `frontend/src/features/matching/labels.ts`：`hasDisplayableTotal`、`displayTotalScore`、`displayConfidence`、`gradeTone`；`unableToJudge` 或等级「无法判断」或 `totalScore` 非有限数字 → 空态「服务端未输出总分 / 无法判断」。
- 消费页：`MatchReportPage.vue`、`ExplainPanel.vue`、`JobConfirmPage.vue`（当前报告/版本史）、`WorkspacePage.vue`（`currentMatch` 块）。
- 迭代日志 2026-08-18 匹配报告展示核修官；`npm run build` 已通过。

**分级：** `已交付`

**建议下一刀：** 无需。不要改 matching 引擎权重。

---

### 1.16 明确不做（P0B+），实现里也未做成正式入口

下列在确认记录 **第 15 节**、后端计划 **2.2**、前端计划 **第 2 节** 禁止纳入 P0A。搜索未发现对应正式路由/菜单：

| 项 | 契约出处 | 代码证据 | 分级 | 下一刀 |
| --- | --- | --- | --- | --- |
| 手机号 / 第三方 / MFA | 12.1.2、13.2 登录禁止列 | 无对应 API/表单；登录页写明不做 | `明确不做（P0B+）` | 不要做 |
| 邮件 / 短信 / 日历 / 完整偏好中心 | 11.3.19–20、第 15 节 | 通知页写明没有邮件短信和偏好中心 | `明确不做（P0B+）` | 不要做 |
| 收藏 / 漏斗 / 批量改状态 | 7.3.16、7.3.26、10.2 | 投递看板无这些按钮 | `明确不做（P0B+）` | 不要做 |
| 录音 / 转写 / 准备包 / 模拟面试 | 11.1.8、第 15 节 | 面试页写明无录音无转写 | `明确不做（P0B+）` | 不要做 |
| 报告 / 主档物理删除按钮 | 4.5.25、7.1.7、10.2 | 简历/岗位页只有归档 | `明确不做（P0B+）` | 硬删只走数据权利（1.1） |
| 完整运营后台 / 双人复核 | 12.4.18、第 15 节 | 无运营工作区 | `明确不做（P0B+）` | 不要做 |
| 工作台卡片布局冻结 | 前端计划 3.3 | 工作台未锁卡片 JSON | `明确不做（P0B+）` | 不要做 |
| 生产 SMTP / 完整 CSRF | S0 交付说明未做 | 重置走 `/internal/dev/mailbox` | `明确不做（P0B+）` | 不挡 P0A 联调最小闭环 |
| 创建/撤销只读分享入口 | 13.2 未列；12.4 导出删除最小集；第 5 轮数据权利细则未开始 | 后端 S0 底座有 API；前端无入口。见 1.11 | `明确不做（P0B+）` | 核过：P0B，不实现 |

---

## 2. 重点核对照（给调度官一眼）

| 重点核 | 结论 | 分级 |
| --- | --- | --- |
| 数据权利导出/删除最小页是否有 UI | 路由 `/account/data-rights`、feature、API 客户端、顶栏/改密/通知/工作台入口均在 | `已交付` |
| 对象级删除预览与编排 | `ObjectDeletionCatalog` + `DeletionOrchestrator` 读 `scope`；前端消费 `impacts` / `canProceed` | `已交付` |
| 证据类型是否齐 | 六类前后端对齐；引用保护与复制在 | `已交付` |
| 简历冻结前置 | 未确认 AI / 未处理成果 / 非可投递均拦截；前端逐条确认 | `已交付` |
| 匹配无法判断 | 仅 4.3.13 两条；第三条件已删；空分母 `total=null`；展示 helper 统一空态 | `已交付` |
| 面试不回写投递 | 服务端只读投递引用；前端无回写按钮 | `已交付` |
| 通知失败语义 | `SEND_FAILED` 不回滚；去重；阶段变化通知核过保留 | `已交付` |
| 账号会话 + 注销 | 邮箱会话、HttpOnly Cookie、退出当前、改密全失效；注销走数据权利页 | `已交付` |
| 简历 PDF 下载 | `TaskView.fileId` + `GET /files/{id}/download` + 前端会话下载 | `已交付` |
| 分享入口 | 后端底座在；前端无入口；契约未列必做 | `明确不做（P0B+）` |

---

## 3. 查阅过的文件（分析取证，非改码）

**契约与计划**

- `开发文档/JobProof-AI-P3.5-业务逻辑确认记录-v0.1.md`
- `docs/plan/JobProof-AI-P0A-后端实施计划.md`
- `docs/plan/JobProof-AI-P0A-前端实施计划.md`
- `docs/plan/JobProof-AI-P0A-数据权利前端交付说明.md`
- `docs/plan/JobProof-AI-P0A-S0-前端交付说明.md` … `S5-前端交付说明.md`、`S0-后端交付说明.md` … `S5-后端交付说明.md`
- `docs/plan/JobProof-AI-P0A-自主迭代日志.md`（最近已交项，未追加）

**后端（抽样到 Controller / 策略 / 编排）**

- `modules/datarights/web/DataRightsController.java`、`application/DataRightsService.java`、`application/DeletionOrchestrator.java`、`application/ObjectDeletionCatalog.java`
- `modules/identity/web/AuthController.java`、`application/IdentityService.java`、`infrastructure/security/SessionAuthFilter.java`
- `modules/evidence/domain/EvidenceType.java`
- `modules/resume/domain/ResumeFreezePolicy.java`、`web/ResumeController.java`
- `modules/matching/application/MatchingService.java`、`domain/MatchScoringEngine.java`
- `modules/interview/application/InterviewService.java`
- `modules/application/application/ApplicationService.java`
- `modules/notification/application/NotificationService.java`、`domain/NotificationType.java`
- `modules/review/application/ReviewService.java`
- `modules/profile/domain/ProfileFactKey.java`
- `modules/task/application/TaskView.java`
- `modules/storage/web/FileController.java`
- `src/test/java/com/jobproof/ObjectDeletionIT.java`、`ResumePdfExportIT.java`、`JobMatchingContractIT.java`

**前端**

- `src/app/router.ts`、`src/shared/ui/AppChrome.vue`、`src/shared/lib/workspaceNav.ts`、`src/shared/api/client.ts`、`src/shared/api/task.ts`
- `src/features/datarights/`（pages、services、labels、types）
- `src/features/identity/pages/*`、`src/features/evidence/*`、`src/features/resume/pages/ResumeEditorPage.vue`、`src/features/application/pages/ApplicationCreatePage.vue`
- `src/features/matching/pages/MatchReportPage.vue`、`labels.ts`、`components/ExplainPanel.vue`
- `src/features/interview/pages/*`、`src/features/notification/pages/NotificationListPage.vue`
- `src/features/profile/pages/ProfilePage.vue`、`src/features/review/pages/*`、`src/features/workspace/pages/WorkspacePage.vue`

---

## 4. 审计声明

- 未修改 `backend/`、`frontend/` 任何文件。
- 未追加 `docs/plan/JobProof-AI-P0A-自主迭代日志.md`。
- 本文件为复审覆盖写入。
- 发现缺口后的实现须另派切片官；本官不动手改业务代码。
