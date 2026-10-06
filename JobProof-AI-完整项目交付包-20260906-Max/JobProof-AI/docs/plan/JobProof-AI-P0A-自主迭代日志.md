# JobProof AI P0A 自主迭代日志

> 编码：UTF-8。调度官 / 迭代官连续推进时追加，不发明业务规则。不 git commit。

---

## 2026-08-18 晚 · 迭代官 · S0 硬化（不开 S1）

### 判定

- 确认记录文内 **v0.7 已冻结**。
- S0 骨架已在：`backend/` + `backend/README.md` + `docs/plan/JobProof-AI-P0A-S0-后端交付说明.md`。
- `mvn test` **未能交接**：`DataRightsAndNotificationIT.exportTaskAndNotificationDedupAndFailureDoNotRollback` 期望 200，实得 500。
- 因此本轮只硬化 S0，**不开 S1，不做前端 / 正式 Mock，不碰 P0B/P1/P2**。

### 本轮做了什么

1. **修 bug（MOCK-NOTIFY-002）**：`NotificationService.request` 模拟渠道失败时在 `REQUIRES_NEW` 里抛异常，外层 `DataRightsService.createExport` 被标 rollback-only，导出创建变成 `SYSTEM_FAILURE`。现改为编程式独立事务：失败不抛给调用方，另开事务写入 `SEND_FAILED`。
2. **会话 Cookie**：登录 Cookie `Max-Age` 改为跟随会话 `expiresAt`，与服务端会话 TTL 一致。
3. **补测试**：`AccountRulesTest`（邮箱 / 弱密码错误码）；`AuthValidationAndSessionIT`（HTTP 校验码 + 双会话改密全失效）；`NotificationChannelFailureIT`（通知失败后导出仍成功且可见 `SEND_FAILED`）。
4. 更新 S0 交付说明第 6 节。

### 如何验证

在 `backend/`：

```bash
mvn test
```

本轮已跑通（exit 0）。重点：`AuthFlowIT`、`AuthValidationAndSessionIT`、`DataRightsAndNotificationIT`、`NotificationChannelFailureIT`。

### 下一刀建议

1. 再跑一遍 `mvn test` 确认绿灯后，调度官可宣布 **S0 后端可交接**（仍不要开 S0 前端）。
2. 下一刀：**S1 画像 + 证据领域层**——「无求职方向不得正式匹配」+ 完整度规则 + 六类证据引用闸，先领域对象与单测，再最小表 / API。仍不要做前端。
3. 禁止：同时开 S0 前端与 S1 后端；同文件双人改。

---

## 2026-08-18 晚 · S1 后端切片官 · 画像 + 证据

### 判定

- 陛下放权，S0 后端已可交接；本刀只做 **S1 画像 + 证据**。
- 不推翻 S0 Identity / 会话 / 删除 / 导出；不写 Vue / 正式 Mock；不开 S2；不 git commit；不用 Python 跑项目。

### 本轮做了什么

1. 新包 `modules.profile`、`modules.evidence`；Flyway `V2__s1_profile_evidence.sql`（正式事实/候选、证据、引用闸）。
2. 冻结规则落地：正式事实须填写或确认；拒绝候选不进匹配/简历；无求职方向 `assertDirectionConfirmed()`；敏感属性拒收；完整度 30/+8/技能或年限补到 100；证据六类与强/中/弱/待补证；已引用改正文 409 并提示复制；归档可恢复。
3. 事件只预留：`ProfileSnapshotChanged`、`EvidenceChanged` 等，不重算匹配。
4. 删除编排 profile/evidence 由本模块回执；导出纳入正式画像与证据元数据。S0 文件短时上传复用。
5. `mvn test`：**28 tests, 0 failures**。契约见 `docs/plan/JobProof-AI-P0A-S1-后端交付说明.md`。

### 如何验证

在 `backend/`：

```bash
mvn test
```

重点：`ProfileRulesTest`、`EvidenceRulesTest`、`ProfileEvidenceIT`，以及 S0 的 `AuthFlowIT` / `DataRightsAndNotificationIT`。

### 下一刀建议

1. 调度官可核交接仪式后宣布 **S1 后端可交接**（仍不要同时开 S1 前端与 S2 后端，除非军令另准）。
2. S2 只读已确认画像与允许使用的证据，匹配入口必须调用 `ProfileService.assertDirectionConfirmed`；输入变化只把 CURRENT 标 STALE。
3. 禁止：发明权重、自动重算、采集敏感属性、同文件双人改。

---

## 2026-08-18 晚 · 巡更当值官 · 第 2 次 15 分钟 tick（S0/S1 硬化）

### 判定

- `docs/plan/JobProof-AI-P0A-S2-后端交付说明.md` **不存在**。按军令：不开 S3，不大改 Job/Matching。
- S0/S1 交付说明与迭代日志在。工作区已有 Job/Matching 半成品，`mvn test` **编译失败**（缺 S2 类型/方法，属另一路切片）。本刀不修那些文件。

### 本轮做了什么

1. **S1 只读口硬化**：`ProfileMatchingGate` 把 JSON 引号空白视为未知；`matchingRead` / 完整度 / `matchingSnapshot` 共用，避免未知字面量进入匹配。
2. **领域单测**：`ProfileRulesTest` 增补引号空白 / 空 JSON 方向不得过门。
3. **补 S1 文档缺口**：请求体、字段语义（不发明强制 schema）、S2 只读领域口表。
4. **S0 文档**：删除编排不再写「画像/证据永远 SKIPPED」；有处理器则回执清理。
5. 禁止项遵守：无前端 / Mock、无 P0B、无 Python 跑项目、无 git commit、未开 S3。

### 如何验证

S2 半成品仍在时，全量 `mvn test` 会编译失败——**不要为此改 Job/Matching**。核 S0/S1 领域：

```bash
# 待 S2 编译恢复后
cd backend
mvn test
```

本轮已用隔离 javac 跑通 S0/S1 领域 **18 tests, 0 failures**（含新测 `quotedBlankOrEmptyJsonIsUnknownDirection`）。不依赖 Job/Matching 编译。

### 下一刀建议

1. 等 S2 交付说明落地且 `mvn test` 全绿，再开 **S3 简历+投递**。
2. S2 切片官收口编译：`InProcessWorker` / `JobService` / `MatchingService` 半成品对齐。
3. 仍不要同时开 S1 前端与 S3 后端；同文件双人改继续禁止。

---

## 2026-08-18 晚 · 巡更当值官 · 第 1 次 15 分钟 tick（收口 S2）

### 判定

- S1 交付说明已在；本 tick 开跑 `mvn test` 时 S0+S1 已绿，按军令开 **S2 JD 文本导入 + 单岗位匹配**。
- 禁止 P0B/P1/P2、禁止正式前端/Mock、禁止 Python 跑项目、不 git commit、不开 S3。

### 本轮做了什么

1. 对齐 Job/Matching 主链：表 `jobs` / `jd_snapshots` / `job_versions` / `match_reports` / 预置 `P0A-RULE-1`；解析复用 `async_tasks`；`JobMatchingWorker` 跑 `JD_PARSE`/`JOB_MATCH`。
2. 匹配入口先校验已确认岗位版本，再 `assertDirectionConfirmed`；混合岗拦截；画像/证据变化只把 CURRENT 标 STALE。
3. 复用导入不再误标 `lengthRejected`；删除编排 `job`/`matching` 各一回执（避免唯一键冲突）。
4. 契约：`docs/plan/JobProof-AI-P0A-S2-后端交付说明.md`；`backend/README.md` 标明 S2 包。
5. `mvn test`：**50 tests, 0 failures**。

### 如何验证

```bash
cd backend
mvn test
```

重点：`JobMatchingIT`、`MatchScoringEngineTest`、`JdTextPolicyTest`、`JdHeuristicParserTest`，以及 S0/S1 回归。

### 下一刀建议

1. 调度官核交接仪式后可宣布 **S2 后端可交接**（仍不要开 S2 前端）。
2. 下一刀只做 **S2 硬化**（补审计/对象级用例、匹配导出贡献）或等调度交接；**不要开 S3**，除非军令明确「S2 已交接」。
3. 继续禁止同文件双人改。

---

## 2026-08-18 晚 · S3 后端切片官 · 简历主档 + 版本 + 投递 CRM

### 判定

- 陛下放权，S0–S2 后端已交付（S2：50 测全绿）。本刀只做 **S3 简历主档 + 版本 + 投递 CRM**。
- 不推翻 S0–S2；不大改 Job/Matching 打分；只引用 `CONFIRMED` 岗位。不写 Vue / 正式 Mock；不开 S4；不 git commit；不用 Python 跑项目。

### 本轮做了什么

1. 新包 `modules.resume`、`modules.application`；Flyway `V4__s3_resume_application.sql`（主档 / 候选 / 版本 / 投递 / 时间线）。
2. 主档四态与归档恢复；AI 只出候选；可投递 / 冻结闸：无未确认 AI + 关键成果须关联证据或逐条确认「暂无证据仍要冻结」。冻结调用 S1 `registerActiveReference`，失败回滚不留半成品。
3. 版本五态；定制任务成功仅为待用户确认；已绑定不可原地改。投递必须绑已冻结 / 已绑定版本 + `CONFIRMED` 岗位；匹配报告不是前置。
4. 投递主链与终止态、前进 / 更正 / 接受后撤回照录确认记录 7.3；乐观并发；归档不解除绑定；进入面试中不建轮次。
5. 删除编排与导出挂接 resume / application。契约：`docs/plan/JobProof-AI-P0A-S3-后端交付说明.md`。
6. `mvn test`：**80 tests, 0 failures**。

### 如何验证

```bash
cd backend
mvn test
```

重点：`ResumeFreezePolicyTest`、`ApplicationStageMachineTest`、`ResumeApplicationIT`，以及 S0–S2 回归。

### 下一刀建议

1. 调度官核交接仪式后可宣布 **S3 后端可交接**（仍不要开 S3 前端）。
2. 下一刀只做 **S4 面试轮次 + 基础复盘**，或等调度交接。
3. 禁止：收藏 / 漏斗 / 批量改状态；Matching 改投递；同文件双人改。

---

## 2026-08-18 晚 · 巡更当值官 · 第 3 次 15 分钟 tick（硬化 S0–S2）

### 判定

- `docs/plan/JobProof-AI-P0A-S3-后端交付说明.md` **不存在**。按军令：不开 S4，不新建/大改 Resume、Application。
- S2 交付说明已在。工作区已有 S3 半成品（V4 + Resume/Application 包），本刀避开那些文件。

### 本轮做了什么

1. 全量 `mvn test` 先绿后补缺口，未改生产业务规则。
2. **规则测试**：硬性技能整体计 1、加分不能抬过硬缺口限等、空限制 10% 按 30:35:25 重分、60–79/低于 60 映射、置信度低、权重冻结、短暂失败最多自动 2 次、仅 CONFIRMED 可匹配。
3. **IT**：他人岗位/报告 403、可能重复不自动合并、同岗名不同公司、混合 JD 不拆、STALE 可对比且归档须主动、导出含岗位元数据；删除回执 job/matching 为 SUCCEEDED。
4. 更新 S2 交付说明第 3/5/6 节、S0 删除编排表述。
5. 禁止项遵守：无前端 / Mock、无 P0B、无 Python 跑项目、无 git commit、未开 S4。

### 如何验证

```bash
cd backend
mvn test
```

本轮全量 **77 tests, 0 failures**（含工作区已出现的 S3 领域单测）。重点：`MatchScoringEngineTest`、`JobMatchingContractIT`、`JobMatchingIT`、`DataRightsAndNotificationIT`。

### 下一刀建议

1. 等 **S3 交付说明**落地且契约可交接后，再开 S4 面试轮次 + 基础复盘 + 最小通知（确认记录第 11 节）。
2. S3 切片官收口 Resume/Application，不要与巡更抢改那些文件。
3. 仍不要开前端 / 正式 Mock。

---

## 2026-08-18 晚 · S3 后端切片官 · 收口交付说明

### 判定

- 巡更第 3 次 tick 时交付说明尚未落盘。现已写 `docs/plan/JobProof-AI-P0A-S3-后端交付说明.md`。
- 全量 `mvn test`：**80 tests, 0 failures**（含 `ResumeApplicationIT`）。未 git commit。未开 S4 / 前端。

### 本轮补了什么

1. 契约说明：API、状态码、冻结闸、投递流转、验收门、未做事项。
2. `backend/README.md` 与 `pom.xml` 标明 S3。健康检查 `slice=S3`。

---

## 2026-08-18 晚 · 巡更当值官 · 第 4 次 15 分钟 tick（硬化 S0–S3）

### 判定

- `docs/plan/JobProof-AI-P0A-S4-后端交付说明.md` **不存在**。按军令：不开 S5，不大改 Interview/Review。
- S3 交付说明已在。工作区已有面试/复盘领域半成品，本刀避开那些文件。
- 开跑 `mvn test` 先绿（80 / 0），随后补 S3 边界测试与文档。

### 本轮做了什么

1. **领域单测**：前进边照录 7.3.20；创建投递只能待投递/已投递；已获Offer 更正回面试中；`GENERATING`/`ARCHIVED` 不可绑定；定制不得直接冻结；未知模板；归档主档不可标可投递。
2. **IT**：`ResumeApplicationContractIT`——导入入候选、复制不带投递、归档恢复、他人 403、waive 须确认、记录已投递、备注公司名不改快照、更正写时间线、待确认定制不得建投递、进入面试中无轮次字段、导出含简历/投递且 pending 仅面试复盘。
3. **回归补断言**：删除回执 `resume`/`application`=`SUCCEEDED`；匹配成功后投递列表为空；S2 导出 `pendingModules` 不再含简历/投递。
4. 更新 S3 交付说明第 3/6.5 节、S2 导出 pending 表述。
5. 禁止项遵守：无前端 / Mock、无 P0B、无 Python 跑项目、无 git commit。开跑时 S4 说明尚无；本 tick 后半 S4 已落地且测试绿，续开 S5（见下节）。

### 如何验证

```bash
cd backend
mvn test
```

重点：`ResumeApplicationContractIT`、`ApplicationStageMachineTest`、`ResumeFreezePolicyTest`、`DataRightsAndNotificationIT`、`JobMatchingIT`。

### 下一刀建议

1. 等 **S4 交付说明**落地且 `mvn test` 全绿，再开 **S5 工作台只读汇总 API**（确认记录工作台读取边界；不锁卡片）。
2. S4 切片官收口面试轮次 + 基础复盘，不要与巡更抢改 Interview/Review。
3. 仍不要开前端 / 正式 Mock。

---

## 2026-08-18 晚 · S4 后端切片官 · 面试轮次 + 基础复盘 + 通知补齐

### 判定

- 陛下放权，S0–S3 后端已交付（S3：80 测全绿）。本刀只做 **S4 面试轮次 + 基础复盘 + 通知补齐**。
- 不推翻 S0–S3；不大改投递状态机。不写 Vue / 正式 Mock；不开 S5；不 git commit；不用 Python 跑项目。

### 本轮做了什么

1. 新包 `modules.interview`、`modules.review`；Flyway `V5__s4_interview_review.sql`。
2. 轮次六类与五态照录确认记录 11.1；无时间不建提醒；结果不改投递；完成不自动建复盘。
3. 复盘手动发起；分析任务五态同第二轮；仅确认建议写入画像 `CONFIRMED_IMPROVEMENTS`；失败/拒绝/未处理不写弱项。
4. 通知复用 S0：面试提醒更新或作废旧条；失败不回滚业务。删除/导出挂接 interview、review。
5. 契约：`docs/plan/JobProof-AI-P0A-S4-后端交付说明.md`。
6. `mvn test`：**100 tests, 0 failures**。

### 如何验证

```bash
cd backend
mvn test
```

重点：`InterviewRoundMachineTest`、`ReviewPolicyTest`、`InterviewReviewIT`，以及 S0–S3 回归。

### 下一刀建议

1. 调度官核交接仪式后可宣布 **S4 后端可交接**（仍不要开 S4 前端）。
2. 下一刀只做 **S5 工作台只读汇总**，或等调度交接。
3. 禁止：录音 / 准备包 / 轮次回写投递；同文件双人改。

---

## 2026-08-18 晚 · 巡更当值官 · 第 4 次 tick 续 · S5 工作台只读汇总

### 判定

- 开跑时无 S4 说明，先硬化 S0–S3。同 tick 内 S4 交付说明落地，`mvn test` 100 绿。按军令改开 **S5 只读汇总 API**，仍不要 Vue / 卡片。
- 避开改 Interview/Review 生产文件；只调用其只读 `list`。

### 本轮做了什么

1. 新包 `modules.workspace`：`GET /api/v1/workspace`。十块独立读取，失败不填默认事实。
2. 待办只派生已有未完成项，无待办表。JSON 不含漏斗/风险/推荐/收藏。无写接口。
3. `TaskService.listOpen` 供任务块读取。健康检查 `slice=S5`。
4. 契约：`docs/plan/JobProof-AI-P0A-S5-后端交付说明.md`。
5. 契约：`docs/plan/JobProof-AI-P0A-S5-后端交付说明.md`。`mvn test`：**102 tests, 0 failures**。

### 如何验证

```bash
cd backend
mvn test
```

重点：`WorkspaceSummaryIT`，以及 S0–S4 回归。

### 下一刀建议

1. 调度官核交接仪式后可宣布 **S5 后端可交接**（仍不要开工作台 Vue / 锁卡片）。
2. P0A 后端主链已齐，不要开 P0B。前端须等各刀「后端可交接」。

---

## 2026-08-18 晚 · 巡更当值官 · 第 5 次 15 分钟 tick（S0 前端未交付 → 只硬化后端）

### 判定

- `docs/plan/JobProof-AI-P0A-S0-前端交付说明.md` **不存在**。工作区无 `frontend/` / `web/` Vue。按军令：**不大改登录页，不开 S1 前端（画像编辑器）**。
- S5 后端交付说明已在。本刀只做后端硬化：`mvn test`、修与前端无关失败、补文档。禁止 P0B / Python 跑项目 / git commit。

### 本轮做了什么

1. 全量 `mvn test` 先红：`WorkspaceSummaryIT.populatedBlocksHaveStableKeysAndStayAccountScoped` 要求 `currentMatch.totalScore` 常在。样本仅有求职方向时报告为 `无法判断`，分数为 null，Jackson `non_null` 省略该键——**不捏总分**，改测试而非假分数。
2. 复盘未绑轮次时 `interviewRoundId` 同样省略，稳定键不再强制该字段。
3. 落地 S5 验收门：无求职方向时 `todos` 含 `PROFILE_DIRECTION` → `/api/v1/profile`。空账号 IT 从 0 条待办改为 1 条。登录后 PUT `/api/v1/workspace` → 405。
4. 补 S5 说明：`/workbench` 别名、`无法判断` 省略 `totalScore`、空账号待办示例。

### 如何验证

```bash
cd backend
mvn test
```

本轮全量 **102 tests, 0 failures**。重点：`WorkspaceSummaryIT`，以及 S0–S4 回归。

### 下一刀建议

1. 等 **S0 前端交付说明**落地后，再开 S0 登录页或按交接开 S1 前端。本轮不要抢 Vue 登录文件。
2. 调度官仍可核 S5 后端交接；不要锁工作台卡片，不要开 P0B。

---

## 2026-08-18 晚 · S0 前端切片官 · 邮箱会话 + 只读工作台

### 判定

- 陛下放权：P0A 后端 S0–S5 已交接（102 测全绿）。按全局规则，后端闭环后才做前端。本刀只做 **S0 前端**。
- 工作区原先无 Vue。新建 `frontend/`（Vue 3 + TS + Vite），不另起第二套框架，不改 backend Java，不 git commit，不用 Python 跑项目。
- 只消费已确认操作：邮箱注册/登录、验证码重置、退出、改密后会话失效、`GET /api/v1/workspace` 十块只读。不发明收藏/漏斗/风险/推荐，不接画像编辑、JD、简历编辑器。

### 本轮做了什么

1. Vite 开发代理：`/api`、`/internal` → `http://127.0.0.1:8080`；`credentials: 'include'` 吃 HttpOnly Cookie。
2. 页面：`/login` `/register` `/reset` `/workspace` `/account`；未登录跳转；登录后游客页回工作台。
3. 工作台只渲染十块；块失败不填默认事实；`href` 只作来源契约，不是写入按钮。阅读排列未冻结。
4. 覆盖正常/加载/空/错误/校验/提交中/未登录跳转；改密须二次确认。
5. `npm run build`（`vue-tsc -b && vite build`）已通过。契约见 `docs/plan/JobProof-AI-P0A-S0-前端交付说明.md`。

### 如何验证

```bash
cd backend
mvn spring-boot:run

cd frontend
npm install
npm run dev
```

浏览器打开 `http://localhost:5173`。重点：注册登录、重置（dev 邮箱读码）、改密后旧会话失效、工作台十块只读。

### 下一刀建议

1. 调度官核交接后可开 **S1 前端（画像 + 证据）**，或 S0 补刀最小通知 / 数据权利（本刀圣旨未点名，故未做）。
2. 禁止：锁工作台卡片；同文件双人改；顺手做 JD/简历编辑器；开 P0B。

---

## 2026-08-18 晚 · 巡更当值官 · 第 6 次 15 分钟 tick（S1 前端未交付 → 只硬化 S0）

### 判定

- **开场**时 `docs/plan/JobProof-AI-P0A-S1-前端交付说明.md` 不存在，按军令走路径 1：不大改画像/证据 Vue，不开 S2 JD 页。
- **收尾**时 S1 前端交付说明已落盘（切片官本轮后半交卷）。本 tick 时间已用于 S0 硬化，不抢开 S2。
- S0 前端交付说明已在。本刀只硬化登录会话小 bug、`mvn test`、文档。禁止 P0B / Python 跑项目 / git commit。

### 本轮做了什么

1. `LoginPage` 的 `next` 不再只看 `startsWith('/')`。`safeNextPath` 拒绝 `//evil.com`、反斜杠、协议相对地址、解码后的开放跳转，以及回登录/注册/重置的循环。
2. `hydrateSession` 并发改为等待同一 Promise，避免路由守卫抢跑时把已登录判成游客。
3. 会话失效回登录时带上安全 `next`（`/account` 失效后可回改密页）。
4. 登录 / 注册 / 重置 / 改密表单 `novalidate`，错误文案走 Vue。
5. 未改 `profile/`、`evidence/`、工作台、路由、backend Java。未开 S2。
6. 全量 `mvn test`：**102 tests, 0 failures**。`frontend` `npm run build`（`vue-tsc -b && vite build`）通过。

### 如何验证

```bash
cd backend
mvn test

cd frontend
npm run build
```

重点：`/login?next=//example.com` 登录后应落 `/workspace`；从 `/account` 会话失效后 `next=/account`；S0–S5 回归。

### 下一刀建议

1. **下一刀开 S2 前端**：JD 粘贴导入、确认字段、单岗位匹配报告（分项/缺口/未知/等级，禁止黑盒只给百分比）。先读 elite-web-designer，只消费 S2 API。
2. 不要抢刚落地的画像/证据文件；不要开 P0B。

---

## 2026-08-18 晚 · S1 前端切片官 · 画像 + 证据

### 判定

- 陛下放权：S0 前端已交付（登录 + 只读工作台）。本刀只做 **画像 + 证据** 页面。
- 复用 `frontend/` Vue 3 + Vite 会话与代理，不另起框架。不 git commit，不用 Python 跑项目，不改 backend Java。
- 不接 JD/匹配报告、简历编辑器、投递看板；不发明收藏漏斗。

### 本轮做了什么

1. 路由：`/profile`、`/evidences`、`/evidences/:id`；顶栏与工作台可导航，契约 `href` 映射到已开门页面。
2. 画像：必填求职方向；建议六项；可后补技能/年限/兴趣/薪资（标明不进匹配）；敏感属性不出现。完整度与匹配前门失败可见。
3. 候选：确认 / 拒绝 / 更正；拒绝条标「非正式事实」。登记候选仅供演示，确认前不进正式事实。
4. 证据：六类创建、列表、归档/恢复、复制新证据；已引用改正文禁用并说明；备注可改。覆盖加载/空/错误/校验/提交中/403。
5. 契约见 `docs/plan/JobProof-AI-P0A-S1-前端交付说明.md`。

### 如何验证

```bash
cd backend
mvn spring-boot:run

cd frontend
npm install
npm run build
npm run dev
```

浏览器打开 `http://localhost:5173`。重点：无方向时匹配前门失败；确认/拒绝候选；创建证据并归档/复制。

### 下一刀建议

1. 调度官核交接后可开 **S2 前端（JD / 匹配）**，或 S0 补刀最小通知 / 数据权利。
2. 禁止：用薪资计分；改已引用正文；顺手做简历编辑器；开 P0B。

---

## 2026-08-18 晚 · S2 前端切片官 · JD 导入 + 确认岗位 + 单岗位报告

### 判定

- 陛下放权：S1 前端已交付，S2 后端契约可消费。本刀只做 **JD 文本导入、确认岗位、单岗位匹配报告**。
- 复用 `frontend/` Vue 3 + Vite 会话与代理。不另起第二套 JD 页。不 git commit，不用 Python，不改 backend Java。
- 不接简历编辑器、投递看板；不发明收藏、多岗位推荐、自动建投递。

### 本轮做了什么

1. 路由收口：`/jobs`、`/jobs/:jobId`、`/jobs/versions/:versionId`（转同一确认页）、`/matching/current`、`/matching/reports/:id`。
2. 导入：80–20000 字校验、任务进度、非 JD / 超短超长失败可见；PENDING/RUNNING 可取消；结构失败只手动重试。
3. 确认：用户校正确认七项；未知打「未知」印，不得显示为符合；仅 CONFIRMED 可匹配；无方向拦截并指向画像。
4. 报告：总分、四维、加分/扣分/缺失、证据、依据、置信、未知项、四档等级。STALE 可查看并提示输入已变化，不自动重算，可手动重算、可对比、可归档。
5. 工作台最近 JD / 当前匹配 / 任务 href 已映射。投递按钮禁用并写明须 S3。
6. `npm run build` 已通过。契约见 `docs/plan/JobProof-AI-P0A-S2-前端交付说明.md`。

### 如何验证

```bash
cd backend
mvn spring-boot:run

cd frontend
npm install
npm run build
npm run dev
```

浏览器打开 `http://localhost:5173/jobs`。重点：字数失败可见；确认未知不显示为符合；无方向不能匹配；报告不是黑盒百分比；STALE 不自动重算。

### 下一刀建议

1. 调度官核交接后可开 **S3 前端（简历冻结 + 投递看板）**，或先联调本刀与 S2 后端。
2. 禁止：自动建投递；收藏；多岗位排名；顺手做面试/复盘页。

---

## 2026-08-18 晚 · 巡更当值官 · 第 7 次 15 分钟 tick（S3 前端未交付 → 硬化 S0–S2）

### 判定

- `docs/plan/JobProof-AI-P0A-S3-前端交付说明.md` **不存在**。按军令：路径 1。不大改简历/投递 Vue，不开 S4 面试页。
- S2 前端交付说明已在。本刀只硬化 S0–S2 小漏。禁止 P0B / Python / git commit。

### 本轮做了什么

1. 工作台任务只把 `JD_PARSE` / `JOB_MATCH` 链到岗位页；导出/复盘任务不再误进导入页。
2. 导入页按任务类型取消/重试；匹配成功打开报告，不伪装成解析成功。
3. 确认页监听 `versionId`；混合岗禁用匹配。最近岗位带上 `versionId`。
4. 「无法判断」不把总分/高置信当作成绩（工作台、报告、对比）。
5. `safeNextPath` 规范化 `..`；`pollTask` 180 秒停刷。未开 S4，未改 resume/application。
6. `mvn test`：**102 tests, 0 failures**。`npm run build` 被切片官正在写的 `ResumeEditorPage.vue` 未用变量挡住（TS6133），本刀未改那些文件。

### 如何验证

```bash
cd frontend
npm run build

cd backend
mvn test
```

重点：工作台失败导出任务不应跳进 `/jobs`；`/jobs?taskId=` 对 JOB_MATCH 走匹配取消口；`/login?next=/jobs/../login` 应落工作台。

### 下一刀建议

1. 等 **S3 前端交付说明**落地后，再开简历冻结 + 投递看板。
2. 不要抢正在写的 resume/application 文件；不要开 S4 面试页，除非 S3 前端已交。
3. 不要开 P0B。

---

## 2026-08-18 晚 · S3 前端切片官 · 简历工作台 + 投递 CRM

### 判定

- 陛下放权：S2 前端已交付，S3 后端契约可消费。本刀只做 **简历中心 / 编辑器 / 投递看板与详情**。
- 复用 `frontend/` Vue 3 + Vite 会话与顶栏。不另起第二套简历页。不 git commit，不用 Python，不改 backend Java。
- 不开面试轮次详情、复盘分析（S4）。不发明收藏、漏斗、批量改状态、物理删除。

### 本轮做了什么

1. 路由收口：`/resumes`、`/resumes/:id`、`/resumes/versions/:versionId`（转同一编辑器）、`/applications`、`/applications/new`、`/applications/:id`。
2. 简历中心：空白 / 模板（四码）/ 导入创建；列表；复制（新草稿、不带投递）；归档 / 恢复。无物理删除按钮。
3. 编辑器：正式字段、AI 候选确认/拒绝/更正、关键成果关联证据或逐条「暂无证据仍要冻结」、标可投递、冻结。未确认禁用可投递/冻结并说明。
4. 版本对比；仅已冻结 / 已绑定可导出 PDF（任务成功可见；查询契约无 fileId，故无浏览器直链）。已有待确认定制版本可人工确认后冻结。
5. 投递：准备投递 / 记录已投递，下拉只列冻结版本 + CONFIRMED 岗位。合法前进、更正填原因、接受后撤回二次确认。非法流转禁用并说明。归档提示不解除绑定。进入面试中提示 S4，不建轮次。
6. 岗位确认页与匹配报告「建立投递」解禁，接到真实创建流。工作台当前简历/投递可导航至此。
7. `npm run build` 已通过。契约见 `docs/plan/JobProof-AI-P0A-S3-前端交付说明.md`。

### 如何验证

```bash
cd backend
mvn spring-boot:run

cd frontend
npm install
npm run build
npm run dev
```

浏览器打开 `http://localhost:5173/resumes`。重点：未确认不能可投递/冻结；无冻结版本或未确认岗位不能建投递；待投递不能直接点面试中；Offer已接受只能接受后撤回；归档不解除绑定。

### 下一刀建议

1. 调度官核交接后可开 **S4 前端（面试轮次 + 基础复盘）**，或先联调本刀与 S3 后端。
2. 禁止：强制建轮次；未确认写弱项；顺手做收藏漏斗；开 P0B。

---

## 2026-08-18 晚 · S4 前端切片官 · 面试轮次 + 基础复盘 + 最小通知

### 判定

- 陛下放权：S3 前端已交付，S4 后端契约可消费。本刀只做 **面试轮次 / 基础复盘 / 最小通知**。
- 复用 `frontend/` Vue 3 + Vite 会话与顶栏。不另起第二套面试页。不 git commit，不用 Python，不改 backend Java。
- 不重做 S5 工作台卡片。不发明录音、准备包、回写投递、未确认写弱项。

### 本轮做了什么

1. 路由：`/applications/:applicationId/interviews`、`/interviews/:id`、`/applications/:applicationId/reviews`、`/reviews/:id`、`/notifications`。
2. 轮次：列表与显式创建（无时间待安排 / 有时间已安排）、详情合法流转、非法按钮禁用并说明允许去向、补时间、清空时间填原因、更正填原因、结果不改投递、乐观版本冲突重读。
3. 复盘：从投递或轮次手动发起；分析任务取消/重试；逐条确认才写弱项、拒绝不进画像；完成时未处理不写入；已确认不可覆盖。
4. 通知：已读/未读/发送失败按后端字段展示；发送失败不能标已读假装已送达。无偏好中心。
5. S3 投递详情接上「面试轮次 / 复盘」入口；进入面试中只提示不建轮次。工作台即将面试/待复盘只接线。顶栏增加面试/复盘/通知。
6. 契约见 `docs/plan/JobProof-AI-P0A-S4-前端交付说明.md`。`npm run build`（`vue-tsc -b && vite build`）已通过。

### 如何验证

```bash
cd backend
mvn spring-boot:run

cd frontend
npm install
npm run build
npm run dev
```

浏览器打开 `http://localhost:5173`。重点：进入面试中不自动建轮次；非法流转禁用；未确认建议不是正式弱项；发送失败不能标已读。

### 下一刀建议

1. S5 工作台只读摘要已存在，只需维持接线，不要锁卡片。
2. 禁止：改 backend；git commit；录音/准备包；回写投递。

---

## 2026-08-18 晚 · 后端巡更加固官 · S0–S5 backend 巡更

### 判定

- 开跑 `mvn test` **先绿**：102 tests, 0 failures。未开 P0B/P1/P2，未改冻结契约语义，未碰 `frontend/`，不 git commit，不用 Python。
- 对照确认记录优先清单复审：发现归档投递仍可改阶段（与 S4 轮次/复盘闸不一致），以及投递/轮次乐观锁只靠可选 `expectedVersion`、并发同版本会后写覆盖。

### 本轮做了什么

1. **软删/归档**：`ApplicationService` 前进 / 更正 / 接受后撤回在已归档时拒绝（`APPLICATION_ALREADY_ARCHIVED`）。备注仍可改。归档不解除已绑定简历版本。
2. **乐观并发**：`applications.version_no`、`interview_rounds.version_no` 加 JPA `@Version`；去掉手工 +1，写路径 `saveAndFlush`。库级冲突映射 `VERSION_CONFLICT`，不再 500。
3. **越权**：补 IT——他人不能读/写别人的投递、轮次、复盘；归档后不能再创建轮次或复盘，投递主状态不变。
4. **未改**：通知渠道已是独立事务，本轮无污染主事务复现；Interview/Review 仍只读 `requireOwnRef`，不回写投递。

### 已观察但不改

- `NotificationType.APPLICATION_STAGE` 不在确认记录 11.3.20 最小类型里，但是已交付行为，本轮不删以免伤 S3 联调。
- JD `confirm` 允许空体，视为用户确认当前解析候选；不改成强制重填七项。
- Resume / Evidence / Review 仍只校验可选 `expectedVersion`，未铺 `@Version`。
- `/internal/dev/mailbox` 匿名可达，但 `dev.mailboxEnabled` 仅 dev/local/test。

### 如何验证

```bash
cd backend
mvn test
```

本轮全量 **104 tests, 0 failures**。重点：`ResumeApplicationIT.archivedApplicationRejectsStageChangesAndKeepsBoundResume`、`InterviewReviewIT.archivedApplicationBlocksInterviewAndReviewWritesAndOthersCannotMutate`，以及原 S0–S5 回归。

### 下一刀建议

1. 前端官继续接 S4；不要与本官抢 `ApplicationService` / `InterviewService`。
2. 不要开 P0B。不要 git commit，除非调度官另旨。

---

## 2026-08-18 晚 · S4 契约补丁官 · 归档闸 + VERSION_CONFLICT

### 判定

- S4 前端已交，但未赶上后端巡更两闸：归档投递仍可点前进/更正/接受后撤回；版本冲突需明确按 409 提示刷新，不当 500。
- 最小补丁。未改 `frontend/src/pages/Workspace/`（工程内为 `features/workspace`，亦未碰）、未改 `backend/`、未大改 router / 顶栏 / 工作台接线。未 git commit。

### 本轮做了什么

1. **`APPLICATION_ALREADY_ARCHIVED`**：投递详情归档后禁用前进 / 更正 / 接受后撤回并写明原因；备注表单仍可改。轮次 / 复盘创建按钮禁用文案带上该 reason。写失败走 409 服务端文案，重读详情。
2. **`VERSION_CONFLICT`**：共享 `isVersionConflict` / `versionConflictMessage`。投递与轮次写命令已带 `expectedVersion`。409 横幅提示刷新并重读，不当系统故障。403 仍走 `ForbidState`。Interview / Review 无改投递阶段按钮。

### 如何验证

```bash
cd frontend
npm run build
```

### 下一刀建议

1. S5 工作台官继续自己的切片，不要回改本刀 Application / Interview / Review 闸。
2. 不要 git commit，除非调度官另旨。

---

## 2026-08-18 晚 · S5 前端官 · 工作台只读汇总收口

### 判定

- S0–S4 前后端已交付。S5 后端 `GET /api/v1/workspace` 为十块只读唯一真相源。S4 已把即将面试 / 待复盘 / 通知链到真实页。
- 本刀只收口工作台：字段、空态、403、无法判断藏分、未确认 AI 不当正式事实。不发明工作台写入。不改 backend。不 git commit。不碰 Application / Interview / Review / Resume 页。不大改 router。

### 本轮做了什么

1. `/workspace` 十块对齐后端键：`profileCompleteness`、`evidenceCompleteness`、`recentJob`、`currentMatch`、`currentResumeVersion`、`currentApplications`、`upcomingInterviews`、`pendingReviews`、`tasks`、`todos`。有数据进已有页；空态给下一步；禁用改阶段 / 建轮次 / 建复盘 / 冻结。
2. 匹配「无法判断」不展示人造总分。简历 `PENDING_USER_CONFIRMATION` / `GENERATING` 标未确认。运营 403 走 `ForbidState`。401 仍走会话失效。
3. 保持 S4 面试 / 复盘锚点与通知链接；加载后再滚到 hash。通知旁路只读 `GET /api/v1/notifications`，本页不能标已读。
4. `workspaceNav` 补齐待办 type 与 `ACCOUNT_EXPORT` / `ACCOUNT_DELETION` 跳转；未删 S4 interviews/reviews 映射。
5. 契约见 `docs/plan/JobProof-AI-P0A-S5-前端交付说明.md`。`npm run build`（`vue-tsc -b && vite build`）已通过。

### 如何验证

```bash
cd frontend
npm run build
npm run dev
```

浏览器打开 `http://localhost:5173`。重点：空账号十块可读且无假分数；无法判断不展示总分；工作台无写入按钮；面试/复盘/通知链接仍在。

### 下一刀建议

1. 数据权利页仍是 S0 尾巴，不在本刀。不要开 P0B。
2. 不要 git commit，除非调度官另旨。不要回改 S4 契约补丁官刚收的归档闸。

---

## 2026-08-18 晚 · 前端回归纠错官 · P0A S0–S5 有证据缺陷

### 判定

- S0–S5 前端交付说明已齐。本刀只改 `frontend/` 与本日志，**未碰 `backend/`**，未 git commit，未用 Python，未开 P0B/P1/P2，未重做视觉，未改冻结契约语义。
- 对照优先清单复审：工作台无写入；十块 href 已映射 S0–S4 实页；登录 `next` 仍走 `safeNextPath`；归档投递/轮次/复盘创建闸已在；工作台「无法判断」已藏分。发现岗位史、报告失败、时间线/证据空态、待确认主档闸仍有实缺。

### 本轮修了什么

1. **无法判断人造总分**：`JobConfirmPage` 本版本报告列表不再渲染 `item.totalScore`。等级为无法判断时写「服务端未输出总分」。
2. **报告失败不当整页失败 / 假空**：`loadReports` 不再把当前报告错误抛回 `load()`（原先会清空已读到的岗位并写成「岗位读取失败」）。列表/当前报告失败单独横幅。
3. **VERSION_CONFLICT 不当 500**：岗位确认、简历编辑器走 `versionConflictMessage`；创建投递 409 走 `errorMessage`（含「这不是系统故障」）。
4. **时间线吞错 / 假失败**：投递详情时间线失败不再伪装成「还没有阶段时间线」；前进/更正/撤回成功后时间线刷新失败不再改口成「前进失败」。
5. **未确认 AI / 证据空态**：主档 `PENDING_CONFIRMATION` 与 pending 候选一样不能标可投递/冻结；证据列表失败可见，不暗示「没有证据可 waive」。
6. **创建投递假空**：全部主档/岗位详情读取失败时，不再显示成「没有可绑定版本 / 没有已确认岗位」。

### 检查过但未改（已有闸或无证据）

- 工作台无 POST/PUT/PATCH/DELETE；十块与顶栏链到已有路由。
- 工作台 / 报告详情 / 对比面板「无法判断」已藏分。
- 归档投递不能前进/更正/撤回；轮次/复盘创建已禁用。
- 登录 `next` 仍拒绝 `//`、协议、反斜杠、`..` 跳出与登录循环。未回潮。
- 通知 `SEND_FAILED` 不能标已读。前端无单测脚本，只跑了类型检查 + 生产构建。

### 如何验证

```bash
cd frontend
npm run build
```

本轮 **`vue-tsc -b && vite build` 通过**（exit 0）。未跑 backend / Python。

### 下一刀建议

1. 后端回归官继续只改 `backend/`。不要与本刀抢前端文件。
2. 不要开 P0B。不要 git commit，除非调度官另旨。

---

## 2026-08-18 晚 · 后端回归纠错官 · P0A S0–S5 契约回归

### 判定

- 开跑 `mvn test` **先绿**：104 tests, 0 failures。并行同事只改 frontend。本刀只改 `backend/` 与本日志，**未碰 `frontend/`**，未 git commit，未用 Python，未开 P0B。
- 对照冻结契约与优先清单：归档闸、乐观锁 409、Interview/Review 不回写投递、未确认 AI 冻结闸、通知独立事务、对象级 403 均已在。发现 **「无法判断」仍会算出并落库总分**，属明确契约违规。

### 本轮修了什么

1. **无法判断不给人造总分**：`MatchScoringEngine` 一旦 `unableToJudge`，`total=null`（缺方向或 ≥4 个未知正式字段时不再用分项加权出总分）。报告解释 JSON 省略 `totalScore`。工作台 `currentMatch` 等级为「无法判断」时强制不输出总分。
2. **VERSION_CONFLICT 不是 500**：`GlobalExceptionHandler` 把 Spring `OptimisticLockingFailureException` 父类一并映射 409 `VERSION_CONFLICT`。
3. **补回归**：归档投递仍可改备注/渠道，但不能前进/更正/接受后撤回；归档后不能改已有轮次/复盘，他人不能读轮次/复盘；投递阶段通知失败不回滚前进；无法判断报告不建投递且工作台无总分。

### 已观察但不改

- `NotificationType.APPLICATION_STAGE`：确认记录 11.3.20 最小类型未列，但 10.5 要求阶段变化通知；与上一轮一样不删。
- JD `confirm` 空体视为确认当前解析候选；Resume/Evidence/Review 仍可选 `expectedVersion`、未铺 `@Version`。
- `/internal/dev/mailbox` 匿名可达，但 `dev.mailboxEnabled` 仅 dev/local/test，维持。

### 如何验证

```bash
cd backend
mvn test
```

本轮全量 **106 tests, 0 failures**（较巡更 104 测 +2：`unableToJudgeReportOmitsFabricatedTotalAndDoesNotCreateApplication`、`applicationStageNotificationFailureDoesNotRollbackAdvance`）。重点：`MatchScoringEngineTest`、`JobMatchingContractIT`、`ResumeApplicationIT`、`InterviewReviewIT`。

### 下一刀建议

1. 前端官继续只改 `frontend/`。不要与本刀抢 Matching / Application / Interview。
2. 不要开 P0B。不要 git commit，除非调度官另旨。

---

## 2026-08-18 晚 · 数据权利前端官 · P0A-blocker 最小页

### 判定

- 缺口清单唯一 `P0A-blocker`：导出/删除/注销没有前端页，后端 API 已在。
- 本刀只改 `frontend/` 与交付/日志文档。**未改 `backend/`**，未改投递/面试/复盘/简历/岗位/匹配页，未改工作台业务逻辑（只把 `ACCOUNT_EXPORT` / `ACCOUNT_DELETION` 从通知改链到数据权利页）。未 git commit，未用 Python，未做邮件短信/MFA/P0B/分享。

### 本轮做了什么

1. 新路由 `/account/data-rights`：预览原文、发起导出、账号删除/注销二次确认、对象级入口（预览非对象字段时禁用提交）。
2. 进度复用 `GET /api/v1/tasks/{id}`；限时下载只在 `downloadAvailable` 或后端给出 `fileId`/`downloadUrl` 时启用，禁止假下载。
3. `/account`、顶栏、通知导出/删除进度硬链可进入此页。
4. 交付说明：`docs/plan/JobProof-AI-P0A-数据权利前端交付说明.md`。

### 如何验证

```bash
cd frontend
npm run build
```

本轮 **`vue-tsc -b && vite build` 通过**（exit 0）。未跑 backend / Python。

对象删除以后端编排与预览字段为准；当前预览若仍是账号级原文，前端不会显示成对象已单独删除成功。

---

## 2026-08-18 晚 · 整链冒烟修复官 · P0A 主路径实跑

### 判定

- 接手时 **5173 Vite** 与 **8080 Spring Boot（dev / H2）** 已在听。第一轮 HTTP 冒烟打出两处真实 500：空/表单 POST 打到带 `@RequestBody` 的确认/拒绝接口，`HttpMediaTypeNotSupportedException` 被收成 `SYSTEM_FAILURE`。另：`SEND_FAILED` 通知走 `POST /notifications/{id}/read` 会被标成 `READ`。
- 只改 `backend/` 与本日志。**未碰 frontend 源码**，未 git commit，未用 Python，未开 P0B/P1/P2，未改冻结契约语义。
- 为加载新 class，已停旧 8080 进程并重新 `mvn -q -DskipTests spring-boot:run`。Vite 未重启。

### 跑了什么

1. PowerShell HTTP 整链（注册/登录/登出/方向闸/证据/JD 导入确认/无法判断藏分/简历冻结闸/投递流转/显式轮次/复盘建议/工作台十块/通知列表/归档 409）。
2. 修后复跑：**58 PASS / 0 FAIL / 2 SKIP**。
3. `mvn test`：**107 / 0**（原 106 + `formEncodedConfirmIsNotInternalError`；`NotificationChannelFailureIT` 原测扩了 SEND_FAILED 标已读闸）。
4. 登录 `next`：`safeNextPath` 实测 `https://`、`//`、`/login`、`\\`、`/profile/../login` 均回落 `/workspace`；合法 `/profile` 放行。工作台 `href` → 实页映射 11 条对齐。
5. `GET http://127.0.0.1:5173/{login,workspace,notifications}` 均 200。Cursor 浏览器 MCP 本环境无法稳定建页，未完成点击走页。
6. 现场复验：表单 POST 确认接口 **HTTP 415** `UNSUPPORTED_MEDIA_TYPE`，不再 500。

### 挂了什么（第一轮）

- `POST /profile/candidates/{id}/confirm`、`POST /resumes/{id}/candidates/{id}/reject` 无 JSON 体时 500。
- 因此待确认简历候选拒绝失败，后续 `ready`/`freeze` 被 `UNCONFIRMED_AI_FACTS` 挡住（连锁，不是冻结语义错）。
- `SEND_FAILED` 无运行态样本（需测试钩子 `failNextWrite`）；靠单测 + 代码闸补上。

### 修了什么

1. **`GlobalExceptionHandler`**：`HttpMediaTypeNotSupportedException` → 415 `UNSUPPORTED_MEDIA_TYPE`；`HttpMessageNotReadableException` → 400 `VALIDATION_FAILED`。不再落 500。
2. **`NotificationService.markRead`**：仅 `DELIVERED` 可标已读；`SEND_FAILED` → 409 `NOTIFICATION_SEND_FAILED`。`markReadBatch` 跳过非 `DELIVERED`，不把失败装成已发送。

### 主路径通/断（修后）

| 路径 | 结果 |
| --- | --- |
| 注册 / 登录 / 会话 / 登出 | 通 |
| `next` 开放重定向 | 通（函数级拒绝；浏览器未点） |
| 未确认方向不能匹配 | 通 |
| 证据创建与引用检查 | 通 |
| JD 导入 → 确认 → 匹配；无法判断 `total=null` | 通（含工作台藏分） |
| 未确认 AI / 未 ready 不能标可投递或冻结 | 通 |
| 建投递绑冻结版本 + CONFIRMED 岗；阶段合法；归档后不能改阶段 | 通（409 `APPLICATION_ALREADY_ARCHIVED`） |
| 进入面试中不自动建轮次；显式建轮次不回写投递 | 通 |
| 复盘建议未确认不当正式弱项；复盘不改投递阶段 | 通（3 条建议均非正式） |
| 工作台十块只读；入口映射实页 | 通（API + href 映射；浏览器未点） |
| 通知最小列表 | 通 |
| SEND_FAILED 不能装已发送 | 通（IT + 后端闸；现场列表无失败样本） |
| 409 VERSION_CONFLICT / APPLICATION_ALREADY_ARCHIVED 不当 500 | 通 |

SKIP：无未冻结版本可负向测（冻结后只剩 FROZEN）；现场无 SEND_FAILED 行。

### 改了哪些文件

- `backend/src/main/java/com/jobproof/infrastructure/web/GlobalExceptionHandler.java`
- `backend/src/main/java/com/jobproof/modules/notification/application/NotificationService.java`
- `backend/src/test/java/com/jobproof/NotificationChannelFailureIT.java`
- `backend/src/test/java/com/jobproof/ProfileEvidenceIT.java`
- 本日志（追加）

### 如何验证

```bash
cd backend
mvn test
mvn -q -DskipTests spring-boot:run
```

另开终端：

```bash
cd frontend
npm run dev
```

健康检查：`GET http://localhost:8080/api/v1/health`（`slice=S5`）。前端：`http://localhost:5173`。

### 下一刀建议

1. 浏览器 MCP 恢复后补点：登录 `?next=https://evil.example` 应落 `/workspace`；工作台十块链进实页。
2. 不要开 P0B。不要 git commit，除非调度官另旨。

---

## 2026-08-18 晚 · 对象级删除后端官 · P0A-gap 对象编排与真实预览

### 判定

- 缺口清单 1.2：`OBJECT` 仍走账号级清理，预览只有固定 IMPACT 文案。属冻结契约 7.1.7 / 7.3.25 / 12.4.17。
- 本刀只改 backend data-rights（及必要测试）+ 本日志 + S0 交付说明一句。**未碰 `frontend/`**，未改 Matching / Resume / Interview / Review 业务状态机，未 git commit，未用 Python，未开 P0B，未做简历 PDF `fileId`。

### 本轮做了什么

1. **预览按对象汇总**：`GET /api/v1/data-rights/deletions/preview?scope=&targetType=&targetId=` 返回 `impacts[]`（kind/id/status/relation/label）、`canProceed`、`blockers`。缺省 `scope=ACCOUNT` 仍为原账号级文案。
2. **对象级编排**：`DeletionOrchestrator` 读 `scope`；`OBJECT` 不再 `markDeletionPending`、不撤全部分享、不删全部私有文件、不取消账号导出、不调各模块 `onAccountDeletion`。按目标删除/级联（投递→面试/复盘；主档→版本及绑定投递）。BOUND/RELATED 对象保留且不改状态机。有效引用证据提交 409 `EVIDENCE_REFERENCED`。
3. **账号级保持**：`ACCOUNT` 仍部分受限（审计索引保留）；导出/注销编排语义不变。
4. **测试**：`DeletionTargetTypeTest` + `ObjectDeletionIT`（预览清单、投递对象删除不伤账号/兄弟对象、主档级联、证据引用闸、他人 403）。

### 如何验证

```bash
cd backend
mvn test
```

本轮全量 **113 tests, 0 failures**。重点：`ObjectDeletionIT`、`DeletionTargetTypeTest`、`DataRightsAndNotificationIT`。

### 下一刀建议

1. 数据权利前端官对接预览 `impacts` / `canProceed` 后再放对象删除按钮。
2. 不要开 P0B。不要 git commit，除非调度官另旨。

---

## 2026-08-18 晚 · 简历 PDF 下载官 · TaskView fileId 与限时下载

### 判定

- 缺口清单 1.10：`POST .../export-pdf` 能建任务，`completePdfExport` 已把 `fileId` 写入成功 payload，但 `TaskView` 不回传，前端无法取文件。属冻结契约 7.2.14 / 10.2。
- 本刀改 task 视图、私有文件字节下载、简历编辑器。**未碰** data-rights 删除编排、matching 引擎、通知类型、分享/P0B。未 git commit，未用 Python。

### 本轮做了什么

1. **任务成功视图**：`GET /api/v1/tasks/{id}` 在 `SUCCEEDED` 且 payload 含 `fileId` 时回传 `fileId`、`downloadAvailable=true`、`downloadUrl=/api/v1/files/{fileId}/download`。非文件任务不加假文件字段。未把整段 payload 暴露出去。
2. **限时下载对齐数据权利语义**：补 `GET /api/v1/files/{id}/download`（本人会话、私有文件字节流）。不发明第二种协议，也不把 `local://` 短链当浏览器直链。他人 403 `OBJECT_FORBIDDEN`。
3. **非法导出**：仍仅 `FROZEN` / `BOUND` 可导出；归档等非法状态 409 `RESUME_VERSION_NOT_EXPORTABLE`。
4. **前端**：冻结/已绑定可点导出；成功后可点「下载 PDF」。无 `fileId` / `downloadUrl` / `downloadAvailable` 时按钮禁用并说明，不假下载。
5. **测试**：`TaskViewDownloadFieldsTest`、`ResumePdfExportIT`。顺带把 `WorkspaceSummaryIT` 改成先冻简历再建匹配，避免匹配后新建证据把 CURRENT 标 STALE（未改匹配引擎）。
6. **验证**：`mvn test` **116 tests, 0 failures**；`npm run build` 通过。

### API 字段与前端如何下载

| 字段 / 接口 | 含义 |
| --- | --- |
| `TaskView.fileId` | 成功 PDF 的私有文件 ID；未成功为 null |
| `TaskView.downloadAvailable` | 成功且有 fileId 为 true |
| `TaskView.downloadUrl` | `/api/v1/files/{fileId}/download`，相对路径，须带会话 |
| `GET /api/v1/files/{id}/download` | 本人限时取字节（与数据权利导出下载一样走会话，不是站外永久裸链） |
| `GET /api/v1/files/{id}/download-url` | 仍是 10 分钟签名地址；本地存储为 `local://`，浏览器不要直接打开 |

前端：任务 `SUCCEEDED` 后，优先 `apiDownload(downloadUrl)`；否则用 `fileId` 打同一 files download。没有这些字段就禁用。

### 如何验证

```bash
cd backend
mvn test

cd ../frontend
npm run build
```

### 下一刀建议

1. 不要开分享站外永久裸链或 P0B。
2. 不要 git commit，除非调度官另旨。

---

## 2026-08-18 晚 · 数据权利页点验官 · 接真实 OBJECT API 并点验

### 判定

- 对象级删除后端官已交刀：`GET /deletions/preview?scope=OBJECT` 返回 `impacts` / `canProceed` / `blockers`，提交不再走整账号清理。
- 本页原先在预览非对象字段时永久禁用对象提交。现改为：账号预览与对象预览分区；仅当对象预览 `scope=OBJECT` 且 `canProceed=true`、无 `BLOCKING` 时开放提交。
- **未改 `backend/` 源码**（为点验重启了 8080 以加载同事已交刀 class）。未改 resume / application / interview / review / job / matching 业务页。未 git commit。未用 Python。

### 点验步骤与通/断

| 步骤 | 结果 |
| --- | --- |
| 未登录进 `/account/data-rights` | 通 → `/login?reason=unauthenticated&next=/account/data-rights` |
| 登录后顶栏「数据权利」 | 通 |
| `/account` 文内链到本页 | 通 |
| 工作台任务 href（`a[href*="/account/data-rights"]`） | 通 |
| 账号级预览有原文 | 通（含账号编排文案；运行态现带 `scope=ACCOUNT`） |
| 导出发起 + 进度 + `downloadAvailable` 才可下 | 通；此前禁用并写明原因；成功后下载 `jobproof-export.json` |
| 对象表单空 / 假 ID | 通：提交禁用；假 ID 刷新 `TARGET_NOT_FOUND`，不拿账号级预览去提交 |
| 真实 EVIDENCE 对象预览 | 通：`scope=OBJECT`、`impacts` TARGET、按钮开放 |
| 对象删除二次确认 | 通；回执范围 OBJECT，不是整号清理文案 |
| 账号删除二次确认 + 进行中不能连点提交 | 通（进度 ACCOUNT / 已提交；刷新按钮仍可点，属只读，不是连点提交） |

浏览器 MCP 当时建不了页，改用本机 Chrome + Playwright（Node，非 Python）。

### 改了哪些前端文件

- `frontend/src/features/datarights/types.ts`
- `frontend/src/features/datarights/labels.ts`
- `frontend/src/features/datarights/index.ts`
- `frontend/src/features/datarights/pages/DataRightsPage.vue`
- `frontend/src/style.css`
- `docs/plan/JobProof-AI-P0A-数据权利前端交付说明.md`
- 本日志

### 如何验证

```bash
cd frontend
npm run build
```

本轮 `npm run build` **通过**（`vue-tsc -b && vite build`）。

### 下一刀建议

1. 工作台 SUCCEEDED 的 `ACCOUNT_EXPORT` 会离开「进行中任务」列表；映射 href 仍在，不必改工作台业务。
2. 不要开 P0B。不要 git commit，除非调度官另旨。

---

## 2026-08-18 晚 · 匹配无法判断核修官 · 1.5b `noJudgedDimension`

### 判定

- 确认记录 **4.3.13** 原文：无法判断**仅当**求职方向缺失，或 7 个正式匹配确认字段中有 4 个及以上为未知、无法形成有效对照。不得因单个未知字段把整份打成无法判断。
- 引擎曾多第三条 `noJudgedDimension`（四维均无已判断分也标无法判断），严于契约。
- 上一轮回归：无法判断必须 `total=null`、不落库人造总分。删第三条件时不得把空分母写成 0 再套「缺口明显」。
- 只改 `MatchScoringEngine` + `MatchScoringEngineTest` + 本日志 + 清单 1.5b 一句。**未碰 frontend、data-rights、Resume PDF / FileController。** 未 git commit，未用 Python，未开 P0B。

### 契约原文 vs 引擎

| 来源 | 无法判断充分条件 |
| --- | --- |
| 4.3.13 | ① 求职方向缺失；② 7 项（岗位名称、主要职责、硬性技能、经验要求、学历要求、地点、工作方式）中 ≥4 未知 |
| 4.4.18 / 4.6.28 | 加分不能改变该等级；失败/取消/无法判断不自动建投递 |
| 改前引擎 | ①② + **③ `noJudgedDimension`** |
| 改后引擎 | 仅 ①②。`MatchingService` 仍写入 `scored.total()`；①② 命中时 `total=null`。闲置 `MatchingScorer` 本就只有 ①②，未改（不在正式路径） |

对照项全未知、但方向已确认且七项未知不足 4：`unableToJudge=false`，加权分母为 0 时 **总分保持 null**（不写 0）。4.4.17 无总分不能映射高度/部分/缺口，等级字段仍为「无法判断」，避免人造总分回潮。

### 本轮修了什么

1. 删除 `noJudgedDimension`。`unable` 只保留无方向或七项中 ≥4 未知。
2. ①② 命中仍强制 `total=null`、加分 0。空分母不写成 0。
3. 补测：全未知对照不是第三触发且无人造总分；单维已判断仍出真实分、不整份无法判断。

### 已观察但不改

- `NotificationType.APPLICATION_STAGE`：对照确认记录 **10.5**「Application CRM → Notification，阶段变化，异步最终一致，通知失败不回滚投递」。清单「多发投递阶段通知」相对 11.3.20 白名单是多做，但 10.5 要求发。**核过，不改。**
- 未改权重、未扩维度、未改 `MatchingService` 落库条件（本就写入 `scored.total()`，无法判断为 null）。

### 如何验证

```bash
cd backend
mvn test
```

本轮全量 **118 tests, 0 failures**（`MatchScoringEngineTest` 18，含本刀 +2）。重点：`MatchScoringEngineTest`、`JobMatchingContractIT`。

### 下一刀建议

1. 并行同事继续 data-rights 前端。不要与本刀抢 matching。
2. 不要开 P0B。不要 git commit，除非调度官另旨。

---

## 2026-08-18 晚 · 简历 PDF 点验官 · 现场点验并修下载交互

### 判定

- 下载官已交刀：`GET /api/v1/tasks/{id}` 成功后带 `fileId` / `downloadAvailable` / `downloadUrl`；下载走 `GET /api/v1/files/{id}/download`（会话）。现场 API 与 Vite 代理均能取到 `%PDF-` 字节，不是 HTML 错误页。
- 点到的真 bug 在 resume 下载交互，不在 files/task 后端：无 `fileId` 时只要 `downloadAvailable` 或任意 `downloadUrl` 就会点亮「下载 PDF」；`http(s)` 走 `window.open` 裸链，违反「会话不是裸链」。
- **未改 backend。** 未改 data-rights 页、account 页、matching 引擎。未 git commit。未用 Python。浏览器 MCP 当时建不了 tab，点验走 8080 + 5173 代理会话请求。

### 点验步骤与通/断

| 步骤 | 结果 |
| --- | --- |
| 未知 version 导出 | 通：400 `RESUME_VERSION_NOT_FOUND` |
| 归档（未冻结/未绑定）导出 | 通：409 `RESUME_VERSION_NOT_EXPORTABLE` |
| 草稿主档 | 通：versions=0，页面无导出钮（未冻结无可导出版本） |
| 冻结后 `POST .../export-pdf` | 通：任务 `RESUME_PDF_EXPORT`，轮询至 `SUCCEEDED` |
| 成功任务视图 | 通：`downloadAvailable=true`，有 `fileId`，`downloadUrl=/api/v1/files/{id}/download` |
| 本人限时下载（8080 与 5173 代理） | 通：`%PDF-1.1`，约 2.4KB，非 HTML |
| 他人 `fileId` | 通：403 `OBJECT_FORBIDDEN`，JSON 非 HTML |
| 未登录下载 | 通：401 JSON（`UNAUTHENTICATED`），非 HTML |
| 无 `fileId` 时下载钮 | 通（修后）：必须有 `fileId` 才启用，不假下载 |

### 改了什么

- `frontend/src/features/resume/labels.ts`：下载钮只认 `fileId`，不再因单独的 `downloadAvailable` 亮起。
- `frontend/src/features/resume/pages/ResumeEditorPage.vue`：去掉裸链 `window.open`；非法导出文案带 `RESUME_VERSION_NOT_EXPORTABLE`。
- `frontend/src/features/resume/services/resumeApi.ts`：只打会话 `/api/v1/files/{id}/download`；校验 `%PDF-`，拒绝 HTML/JSON 当 PDF。
- `frontend/src/shared/api/client.ts`：`apiDownload` 不跟随重定向、拒绝 `text/html`（避免把网页存成文件）。这是共享文件客户端，data-rights 下载也会更严，但未改 data-rights 页面。

`npm run build` 已过。后端未改，未重跑 `mvn test`。

### 下一刀建议

1. 不要开分享站外永久裸链或 P0B。
2. 不要 git commit，除非调度官另旨。

---

## 2026-08-18 晚 · 匹配报告展示核修官 · 空分母总分 null 展示

### 判定

- 确认记录 **4.3.13**：无法判断仅两条（无方向，或七项中 ≥4 未知）。同事已删引擎第三条 `noJudgedDimension`。
- 确认记录 **4.4.17**：无总分不能映射高度/部分/缺口。新边（方向已确认、七项未知不足 4、对照项全未知）`unableToJudge=false`，加权分母 0 时 **总分仍为 null**，等级字段仍为「无法判断」。
- 确认记录 **4.4.18**：加分不能改变该等级。
- 本刀只修展示：`totalScore==null` 或等级「无法判断」不得渲染人造 0 分或「高度匹配」绿印。空态统一写「服务端未输出总分 / 无法判断」。
- **未碰** `features/resume`、data-rights、account、backend matching 引擎。未改评分逻辑。未开 P0B。未 git commit。

### 查了哪些页面

| 面 | 结论 |
| --- | --- |
| `matching/labels.ts` | 改前无法判断写「不作等级依据」、无分写「—」，与岗位史/工作台不一致；无分时 `gradeTone` 仍可能按「高度匹配」上色 |
| 工作台 `currentMatch` | 已藏无法判断分，但只认等级、空态少「/ 无法判断」；新边 `total=null` 须同一套空态 |
| 报告详情 `MatchReportPage` | `unableToJudge` 或等级无法判断已藏分；对比面板前缀「总分」+「—」不一致；无分时仍可能露出 ≥80 高度匹配说明 |
| `ExplainPanel` | 分项本就「未计分」不写 0；补空态，避免对照全未知被当成 0 |
| 岗位确认当前报告 / 本版本史 | 史已对无法判断藏分；当前报告原先不展示总分；无分且非 4.3.13 旗标时可能写成「总分 —」 |
| `MatchCurrentPage` | 只跳 CURRENT，无分数字，本来就对 |
| matching 源码无 `totalScore ?? 0` | 通知列表/画像完成度的 `?? 0` 不是匹配总分，未改 |

### 本轮修了什么

1. `hasDisplayableTotal`：`unableToJudge`、等级「无法判断」、或 `totalScore` 非有限数字 → 一律空态「服务端未输出总分 / 无法判断」，禁止写成 0。
2. `gradeTone` 无展示总分时强制 `unknown`，不套高度匹配样式。
3. 工作台、报告详情、对比面板、岗位当前报告、岗位史走同一套 helper。
4. 无总分时隐藏「≥80 高度匹配」映射说明。

### 如何验证

```bash
cd frontend
npm run build
```

本轮 `npm run build` **通过**（`vue-tsc -b && vite build`）。

### 下一刀建议

1. 不要开 P0B。不要改 matching 引擎权重。
2. 不要 git commit，除非调度官另旨。

---

## 2026-08-18 晚 · 分享入口核修官 · 核过不实现

### 判定

- 冻结契约 **13.2** 数据权利已确认操作 = 导出、对象/账号删除申请；禁止列是「隐藏不可逆影响」，**没有**「创建分享」。
- **12.4** 标题是「导出与删除最小集」，条目只覆盖导出范围、异步限时下载、对象/账号删除、法定例外。
- **7.2.14**：「分享走数据权利，默认只读、限时、可撤销」是机制约束，不是 P0A 工作区必做按钮。权限表里的「被分享人只读」同理。
- 第 5 轮「运营、权限细化、数据权利细则」状态为不挡 P0A、未开始。架构：导师协作不进 P0A。
- PRD 9.15 / DATA-FR-001 把授权中心写成 P0A 基线，但已确认边界回写到确认记录第 12 节（登录、导出、删除），不以 PRD 愿望清单升格产品入口。
- 后端 S0 已有 `POST /api/v1/data-rights/shares` 与 `.../revoke`，属底座；缺口清单 1.11 原先标 `P0A-gap` 且写明不要升 blocker。
- 军令：契约若列为 P0B / 明确不做，只批注、不写功能代码。

### 本轮做了什么

1. 缺口清单 **1.11** 改分级为 `明确不做（P0B+）`，加「核过：P0B，不实现」。
2. 缺口清单 **1.15** 增一行：创建/撤销只读分享入口。
3. **未写功能代码。** 未改 `backend/`、`frontend/`。未碰 `features/matching`、`features/job`、工作台分数卡片、`features/datarights`、`shared/api/client.ts`。未 git commit，未用 Python，未做邮件短信/公开广场/P0B 社交。

### 如何验证

无需 `mvn test` / `npm run build`（本刀只改两份 Markdown）。

### 下一刀建议

1. 不要为「分享无入口」补 UI 或新 API。
2. 不要 git commit，除非调度官另旨。

---

## 2026-08-18 晚 · 数据权利下载回归官 · 导出 JSON 未被 PDF 点验误伤

### 判定

- 简历 PDF 点验收紧了共享 `apiDownload`：不跟随重定向、拒绝 `text/html`。导出文件是 `jobproof-export.json`（后端 `Content-Type: application/json`），**不能**在共享层只认 PDF。
- 当时 `apiDownload` **没有**全局 `%PDF-`。`%PDF-` 只在 `resumeApi.assertResumePdfBlob`。导出主路径 `downloadAvailable → GET .../exports/{id}/download` 仍通。
- 真缺口：数据权利页对相对 `downloadUrl` 直调 `apiDownload`，没有 JSON 点验；共享 Accept 曾把 PDF 放最前。未改 backend、matching/job/workspace、简历编辑器。未 git commit。未用 Python。复用 8080 / 5173。

### 点验（5173 代理，登录后真实导出）

| 步骤 | 结果 |
| --- | --- |
| 注册 + 登录 | 通：200，Cookie 会话 |
| `POST /api/v1/data-rights/exports` | 通：得到 `exportId` |
| 轮询至 `downloadAvailable` | 通：`taskStatus=SUCCEEDED` |
| `GET .../exports/{id}/download` | 通：200，`application/json;charset=UTF-8`，`filename=jobproof-export.json`，约 627B，以 `{"scope":"ACCOUNT"` 开头，不是 HTML / `%PDF-` |
| 未登录同地址 | 通：401 JSON，不是 HTML 登录页 |

### 改了什么

- `frontend/src/shared/api/client.ts`：`Accept` 改为 json / octet-stream / pdf；拒空 body。仍拒 HTML、不跟随重定向。**不加** `%PDF-`。
- `frontend/src/features/datarights/services/dataRightsApi.ts`：`downloadExportBytes` / `downloadExportByPath` 在取回后验 JSON 魔术字节；相对路径只允许本站 `/api/`。
- `frontend/src/features/datarights/pages/DataRightsPage.vue`：相对 `downloadUrl` 改走 `downloadExportByPath`，不再直调 `apiDownload`。
- 本交付说明补了一句下载分层；本日志。

### 未改

- `backend/`、`features/matching`、`features/job`、`features/workspace`、简历编辑器 / `resumeApi` 的 `%PDF-` 校验。

`npm run build`（`vue-tsc -b && vite build`）**通过**。

---

## 2026-08-18 晚 · 复冒烟修复官 · 改后主路径再验

### 判定

- 接手时 **8080**（`mvn -q -DskipTests spring-boot:run`，dev/H2，slice=S5）与 **5173** Vite 已在听，未重启。禁止 Python。未 git commit。未开 P0B（分享仍核为不做）。
- 对照上一轮整链 58/0 之后的改动：无法判断第三条已删、匹配展示禁人造 0、对象级删除、数据权利页、简历 PDF、`apiDownload` 拒 HTML。本刀只复验主路径；**产品代码无回归，未改 `backend/` / `frontend/`。**
- 未重写缺口清单。只追加本日志。

### 跑了什么

1. 复用 8080 + 5173。Node HTTP 整链（注册登录会话、`next` 不开放重定向、画像/证据/JD/匹配含 4.3.13 与空分母新边、简历冻结闸、PDF 本人 `%PDF-` / 他人 403 / 5173 代理、投递绑定冻结版本+CONFIRMED、归档 409、面试不自动建轮次且不回写、复盘未确认不当弱项、工作台十块只读、导出 JSON 非 HTML、账号删除二次确认、对象删除 `scope=OBJECT` 预览与 BLOCKING 禁提交、成功不是整号清理、空/表单 POST 非 500）。
2. 现场 **95 PASS / 0 产品 FAIL / 1 SKIP**。另有一条辅助断言过严（`高度匹配` + 数值 `0`）：契约禁的是 **人造 0**（`total=null` / 等级无法判断），不是把合法有限分藏掉；引擎高度匹配本就 ≥80，现场空分母与无法判断报告均无 `totalScore` 键。不按回归改前端。
3. `mvn test`：**118 / 0**（含 `NotificationChannelFailureIT` 的 SEND_FAILED 标已读闸、`MatchScoringEngineTest` 18、`ObjectDeletionIT`、`ResumePdfExportIT`）。
4. 未改前端，**未跑** `npm run build`。

### 主路径通/断

| 路径 | 结果 |
| --- | --- |
| 注册 / 登录 / 会话 / 登出 / 再登 | 通 |
| `next` 开放重定向 | 通（`safeNextPath`：`https://`、`//`、`/login`、`\\`、`/profile/../login` 回落 `/workspace`；`/profile` 放行） |
| 未确认方向不能匹配 | 通 `DIRECTION_NOT_CONFIRMED` |
| 证据创建与引用检查 | 通 |
| 4.3.13 无法判断（七项 ≥4 未知）`unableToJudge=true`，无 `totalScore` | 通 |
| 新边：方向已确认、未知不足 4、空加权分母；`unableToJudge=false`，等级仍「无法判断」，无人造总分/0 | 通 |
| 有对照后的真实打分有总分；匹配不自动建投递 | 通 |
| 未确认 AI 不能 ready/冻结 | 通 `UNCONFIRMED_AI_FACTS` |
| 冻结后 PDF：本人 `%PDF-`（8080 与 5173 代理），他人 403 JSON，未登录 401 JSON，非 HTML | 通 |
| 投递绑冻结版本 + CONFIRMED；未确认岗 409；阶段合法；归档后不能改阶段 | 通 `APPLICATION_ALREADY_ARCHIVED` |
| 进入面试中不自动建轮次；显式建轮次不回写投递 | 通 |
| 复盘建议未确认不当正式弱项；复盘不改投递阶段 | 通（`CONFIRMED_IMPROVEMENTS` 空） |
| 工作台十块只读；POST/PUT 405；href 映射实页；无法判断藏分 | 通 |
| 导出 JSON 以 `{` 开头，`application/json`，不是 HTML/`%PDF-`；未登录 401 JSON | 通 |
| 账号删除无 `confirmationAck` 400；二次确认后 SUBMITTED | 通 |
| 对象预览 `scope=OBJECT` 有 `impacts`；引用证据 BLOCKING/`EVIDENCE_REFERENCED` 不能提交；无引用对象删除 COMPLETED 后账号仍 ACTIVE、兄弟对象仍在 | 通 |
| 空/表单 POST 不是 500（415 `UNSUPPORTED_MEDIA_TYPE` / 400） | 通 |
| SEND_FAILED 不能标已读 | 现场无失败样本 SKIP；`mvn test` `NotificationChannelFailureIT` 通 409 `NOTIFICATION_SEND_FAILED` |

### 改了哪些文件

- 本日志（追加）
- **未改** 任何 `backend/` / `frontend/` 源码

### 如何验证

健康检查：`GET http://127.0.0.1:8080/api/v1/health`（`slice=S5`）。前端：`http://127.0.0.1:5173`。

```bash
cd backend
mvn test
```

### 下一刀建议

1. SEND_FAILED 现场样本仍依赖测试钩子 `failNextWrite`，不要为此加生产失败开关。
2. 不要开 P0B / 分享入口。不要 git commit，除非调度官另旨。

---

## 2026-08-18 晚 · 浏览器点验修复官 · 只改 frontend 真 bug

### 判定

- 复用现场 **8080**（`slice=S5`，H2）与 **5173** Vite，未重启。禁止 Python。未 git commit。未开 P0B / 分享入口。**未碰 `backend/`**。未改缺口清单全文。
- HTTP 复冒烟 95/0 之后用 Node Playwright 登录点页。产品主链大多已通；点到的真 bug 是 **未勾选确认时按钮假亮**，以及 **无法判断报告把加分写成 +0**。

### 点了哪些页（通/断）

| 页 | 结果 |
| --- | --- |
| 登录 `next=https://evil.example/steal` / `phish.example` | 通：回 `/workspace`，不开放重定向 |
| `/workspace` 十块 + 顶栏 | 通：十块标题在；本页只有「退出 / 重新读取」，无冻结/建轮次/前进；打开画像、证据、岗位、简历、投递皆实页 |
| 顶栏 面试/复盘锚点、通知、改密、数据权利 | 通 |
| `/matching/reports/{有分}` | 通：高度匹配 + 100 用 `grade-seal--high`，这是合法有限分 |
| `/matching/reports/{无法判断}` | 修后通：`grade-seal--unknown`，总分空态「服务端未输出总分 / 无法判断」，**不再写 +0** |
| `/resumes/{已冻结}` | 通：可冻新版本；未导出时无下载钮；导出成功后出现 fileId，下载可点，不是 HTML/JSON |
| `/resumes/{未确认候选}` | 通：「未确认，不能标可投递」「未就绪，不能冻结」皆 disabled |
| `/applications/{待投递}` | 通：非法流转写明；前进只亮允许的 2 个 |
| `/applications/{id}/interviews` | 通：空列表「还没有轮次。0 轮合法」，须点「创建这一轮」，不自动冒轮次 |
| `/applications/{已归档}` | 通：前进按钮 0 个亮，归档文案在 |
| `/notifications` | 通 |
| `/reviews/{不存在}` | 通：展示「复盘不存在」，不把空表单当成功 |
| `/account` | 修后通：未勾选改密影响时提交禁用 |
| `/account/data-rights` | 修后通：导出 / 账号删除 / 对象删除分区；未勾选时发起导出与账号删除禁用；无对象预览禁用提交；引用证据 BLOCKING / `EVIDENCE_REFERENCED` 禁提交 |

### 本轮修了什么

1. `DataRightsPage.vue`：发起导出须先勾 `exportAck`；账号删除须先勾两条确认；对象删除须对象预览可走且已勾影响确认。BLOCKING 仍走既有 `objectBlocked`。
2. `AccountPage.vue`：未勾选改密影响时提交禁用，文案改为「请先勾选改密影响」。
3. 匹配展示：`displayBonusScore`——无法判断 / 无总分写「未计分」，禁止 `+0`。报告详情、解释面板、岗位当前报告、对比左右栏都带上 `unableToJudge`，无分时不套高度匹配色。

### 改了哪些前端文件

- `frontend/src/features/datarights/pages/DataRightsPage.vue`
- `frontend/src/features/identity/pages/AccountPage.vue`
- `frontend/src/features/matching/labels.ts`
- `frontend/src/features/matching/pages/MatchReportPage.vue`
- `frontend/src/features/matching/components/ExplainPanel.vue`
- `frontend/src/features/job/pages/JobConfirmPage.vue`
- 本日志

### 未改

- `backend/` 全部
- 缺口清单全文
- 分享入口 / P0B

`npm run build`（`vue-tsc -b && vite build`）**通过**。

### 下一刀建议

1. 不要开 P0B / 分享入口。
2. 不要 git commit，除非调度官另旨。

---

## 2026-08-18 晚 · 后端对抗核修官 · P0A 契约洞复现与核修

### 判定

- 现场复冒烟 95/0、`mvn test` 当时 118/0。并行同事只改 frontend。本刀只改 `backend/` 与本日志，**未碰 `frontend/`**，未 git commit，未用 Python，未开 P0B / 分享入口。
- 对照冻结契约优先项复扫：越权、面试/复盘回写、人造总分、归档改阶段、对象删除变整号、通知污染主事务、SEND_FAILED 标已读均已有闸且本轮再打未破。
- **新洞（已复现）**：缺查询参数 / 缺 multipart 文件 part / 未知路径被 `Exception` 兜底收成 500 `SYSTEM_FAILURE`。空 JSON body 与错误 Content-Type 上一轮已是 400/415，本轮回归仍非 500。

### 测了什么

1. 新建 `ContractAdversaryIT`：空 body、`application/x-www-form-urlencoded`、JSON `null`、缺 `jobVersionId`/`with`、缺 file part、`page=abc`、未知路径；以及他人读文件/download-url/任务/导出下载、把他人 `fileId` 写进证据、标他人通知已读。
2. 回归：`InterviewReviewIT`（不回写、归档闸、他人 403）、`JobMatchingContractIT`（无法判断无总分）、`ObjectDeletionIT`（对象删除账号仍 ACTIVE）、`NotificationChannelFailureIT`（SEND_FAILED 409）。
3. 全量 `mvn test`。

### 修了什么

`GlobalExceptionHandler`：缺参 / 缺 part / 绑定失败 / multipart 异常 → 400 `VALIDATION_FAILED`；无路由 `NoResourceFoundException` → 404 `NOT_FOUND`。不再落 500。

### 已观察但不改

- Resume / Evidence / Review 仍可选 `expectedVersion`，未铺 `@Version`。
- `NotificationType.APPLICATION_STAGE` 保留（10.5 要求阶段变化通知）。
- `/internal/dev/mailbox` 匿名可达，仅 `dev.mailboxEnabled` 的 dev/local/test。
- SEND_FAILED 标已读 IT 已是 409；现场无失败样本，未加生产失败开关。

### 如何验证

```bash
cd backend
mvn test
```

本轮全量 **120 tests, 0 failures**（原 118 + `ContractAdversaryIT` 2）。未跑 frontend / `npm run build`。

### 下一刀建议

1. 不要开 P0B / 分享入口。不要 git commit，除非调度官另旨。
2. 前端同事继续只改 `frontend/`。

---

## 2026-08-18 晚 · 前端错误码展示官 · P0A 4xx 不再写成系统故障

### 判定

- 后端对抗已把缺参 / 缺 part / 绑定失败 / multipart 收成 **400 `VALIDATION_FAILED`**，无路由收成 **404 `NOT_FOUND`**，错误 Content-Type 仍是 **415 `UNSUPPORTED_MEDIA_TYPE`**，不再落 `SYSTEM_FAILURE`。`mvn test` 120/0。
- 本刀只改 `frontend/` 错误展示与本日志。**未碰 `backend/`**。未开 P0B / 分享入口。未 git commit。
- 各页已普遍走 `errorMessage()`；409 `VERSION_CONFLICT` / `APPLICATION_ALREADY_ARCHIVED` 的戳记风格保留，4xx 对齐「这不是系统故障」。

### 改了哪些映射

`frontend/src/shared/api/types.ts` 的 `errorMessage`：

| 条件 | 展示 |
| --- | --- |
| 400 `VALIDATION_FAILED`（及误标成 `SYSTEM_FAILURE`/`BAD_RESPONSE` 的 400） | 服务端 `message` 或「请求参数不正确」+ `（400 VALIDATION_FAILED。这不是系统故障。）` |
| 415 / `UNSUPPORTED_MEDIA_TYPE` | 服务端 `message` 或「请求内容类型不受支持…」+ `（415 UNSUPPORTED_MEDIA_TYPE。这不是系统故障。）` |
| 精确 `NOT_FOUND`（及误标成系统故障的 HTTP 404） | 服务端 `message` 或「接口不存在」+ `（404 NOT_FOUND。这不是系统故障。）` |
| 其它 `*_NOT_FOUND` | 服务端文案或「对象不存在」，**不**写成系统故障 / 500 |
| 其它 4xx | 服务端文案；禁止用「系统繁忙 / 系统故障 / 500」兜底 |
| 401 / 403 | **保持原样**：`message` 或页面 fallback；403 仍走 `ForbidState` |
| 409 `VERSION_CONFLICT` / `APPLICATION_ALREADY_ARCHIVED` | 保持原戳记 |

`frontend/src/shared/api/client.ts`：无法解析的 HTTP 4xx **不再**丢 `SYSTEM_FAILURE` / `BAD_RESPONSE` / 「服务响应无法解析」。按状态落到 `VALIDATION_FAILED` / `NOT_FOUND` / `UNSUPPORTED_MEDIA_TYPE` 等。5xx 与下载 HTML/空文件仍是系统故障。

### 页面

未重做页面。投递/面试/复盘/简历/证据/岗位/匹配/数据权利/登录等凡调用 `errorMessage()` 的横幅，自动吃到新映射。`InterviewDetailPage` / `ReviewDetailPage` 的 `isNotFound`、`ApplicationDetailPage` 的 409 分支未改。

源码检索：`系统故障` 仅出现在 409/4xx 的「这不是系统故障」戳记；无把 4xx 写成 500 的页面分支。原先的误伤在共享映射层。

### 如何验证

在 `frontend/`：

```bash
npm run build
```

本轮 `vue-tsc -b && vite build` **通过**。

### 未改

- `backend/` 全部
- 缺口清单全文
- 分享入口 / P0B

### 下一刀建议

1. 不要开 P0B / 分享入口。不要 git commit，除非调度官另旨。

---

## 2026-08-18 晚 · 异常码现场复验官 · 重启 8080 后复打对抗码

### 判定

- `GlobalExceptionHandler` 已把缺参/缺 part/绑定/multipart 收成 400 `VALIDATION_FAILED`，无路由 404 `NOT_FOUND`。接手时 8080 仍是 **22:42 旧进程**（Java PID 53320），吃不到新 handler。
- 只停本项目 8080：`JobProofApiApplication` PID 53320 + 对应 `spring-boot:run` Maven 启动器 PID 54504。未杀无关 Java。
- 5173 Vite 复用，未重启。禁止 Python。未 git commit。未开 P0B。**未改 `backend/` / `frontend/` 源码**（现场 0 条 500，无需再修 handler）。

### 重启

- `cd backend` → `mvn -DskipTests spring-boot:run`（dev/H2，slice=S5）。
- 新进程 `Started JobProofApiApplication`，`GET /api/v1/health` → **200 UP**。

### 现场会话复打（Node HTTP，非 Python）

对抗用例（断言不是 500，应为 400/404/415；`facts` 走 PUT 故 POST 表单为 405，仍非 500）：

| 用例 | 状态 | reason |
| --- | --- | --- |
| GET /api/v1/health | 200 | UP / S5 |
| login empty json | 400 | VALIDATION_FAILED |
| login form | 415 | UNSUPPORTED_MEDIA_TYPE |
| login json null | 400 | VALIDATION_FAILED |
| prepare no body | 400 | VALIDATION_FAILED |
| prepare empty json | 400 | VALIDATION_FAILED |
| facts form（POST） | 405 | METHOD_NOT_ALLOWED |
| resume json null | 400 | VALIDATION_FAILED |
| files json | 415 | UNSUPPORTED_MEDIA_TYPE |
| files multipart missing part | 400 | VALIDATION_FAILED |
| matching current missing param | 400 | VALIDATION_FAILED |
| matching list missing param | 400 | VALIDATION_FAILED |
| matching compare missing with | 400 | VALIDATION_FAILED |
| resume compare missing with | 400 | VALIDATION_FAILED |
| notifications page=abc | 400 | VALIDATION_FAILED |
| notifications size=nope | 400 | VALIDATION_FAILED |
| unknown path | 404 | NOT_FOUND |
| job confirm json null | 400 | VALIDATION_FAILED |
| deletion empty json | 400 | VALIDATION_FAILED |
| read-batch text/plain | 415 | UNSUPPORTED_MEDIA_TYPE |

抽打：

| 用例 | 状态 | reason / 断言 |
| --- | --- | --- |
| 他人文件 download / download-url | 403 | OBJECT_FORBIDDEN |
| 无法判断报告 | 200 | `unableToJudge=true`，无 `totalScore` |
| 归档后 advance | 409 | APPLICATION_ALREADY_ARCHIVED |

现场 **33 PASS / 0 FAIL / 0 条 500**。

### 改了哪些文件

- 本日志（追加）
- **未改** 任何 `backend/` / `frontend/` 源码

### 如何验证

健康检查：`GET http://127.0.0.1:8080/api/v1/health`（`slice=S5`）。前端：`http://127.0.0.1:5173`（本轮未重启）。

```bash
cd backend
mvn test
```

本轮全量 **120 tests, 0 failures**（含 `ContractAdversaryIT` 2）。未跑 frontend / `npm run build`。

### 下一刀建议

1. 不要开 P0B / 分享入口。不要 git commit，除非调度官另旨。
2. 现场 8080 已吃到新 handler；后续复验可复用，勿再误杀无关 Java。

---

## 2026-08-18 晚 · 405 展示补丁官 · METHOD_NOT_ALLOWED 不再写成系统故障

### 判定

- 现场复打 `facts` 用 POST（接口是 PUT）得 **405 `METHOD_NOT_ALLOWED`**。400 / 404 / 415 已在 `errorMessage()` 盖「这不是系统故障」戳；405 只在 `client.ts` 解析成可纠正，展示仍走「其它 4xx」无戳。
- 本刀只改 frontend 共享错误映射与本日志。**未碰 `backend/`**。未重做页面。未开 P0B / 分享入口。未 git commit。

### 映射变化

`frontend/src/shared/api/types.ts` 的 `errorMessage`：

| 条件 | 展示 |
| --- | --- |
| 405 / `METHOD_NOT_ALLOWED` | 服务端 `message` 或「请求方法不受支持」+ `（405 METHOD_NOT_ALLOWED。这不是系统故障。）` |
| 413 / `PAYLOAD_TOO_LARGE` | 服务端 `message` 或「请求体过大」+ `（413 PAYLOAD_TOO_LARGE。这不是系统故障。）` |
| 429 / `TOO_MANY_REQUESTS` | 服务端 `message` 或「请求过于频繁，请稍后再试」+ `（429 TOO_MANY_REQUESTS。这不是系统故障。）` |
| 400 / 404 / 415 | 保持上一刀戳记 |
| 其它 4xx | 仍走 `correctableCopy`：服务端文案或「请求无法处理」，**不**写系统故障 / 系统繁忙 / 500 |

`frontend/src/shared/api/client.ts` 的 `unparsedHttpError`：

- 405 兜底文案改为「请求方法不受支持」（与展示层一致）。
- 新增 413 / 429 解析，避免无法解析时落到 `SYSTEM_FAILURE` / `BAD_RESPONSE`。
- 其余未点名的 4xx 收成 `USER_CORRECTABLE` / `CLIENT_ERROR` / 「请求无法处理」，不再丢默认系统故障。5xx 与下载 HTML/空文件仍是系统故障。

### 页面

未改页面。画像 `facts` 写入等凡调用 `errorMessage()` 的横幅自动吃到 405 戳记。

### 4xx 再扫

- 已点名：400 / 401 / 403 / 404 / 405 / 409 / 413 / 415 / 422 / 429。
- 未点名 4xx（406 / 408 / 410 等）：解析不再是 `SYSTEM_FAILURE`；展示禁止系统故障文案。
- 5xx 仍可写系统繁忙兜底。

### 如何验证

在 `frontend/`：

```bash
npm run build
```

本轮 `vue-tsc -b && vite build` **通过**。

### 未改

- `backend/` 全部
- 页面重做
- 缺口清单全文
- 分享入口 / P0B

### 下一刀建议

1. 不要开 P0B / 分享入口。不要 git commit，除非调度官另旨。

---

## 2026-08-18 晚 · 剩余 500 扫荡官 · 用户可纠正请求不再落 SYSTEM_FAILURE

### 判定

- 现场已确认：缺参 / 缺 part / 绑定 / multipart → 400；无路由 → 404；错误 Content-Type → 415；`mvn test` 当时 120/0。并行同事改 frontend 405 文案。本刀 **只改 backend** 与本日志。未碰 `frontend/`。未开 P0B / 分享入口。未 git commit。

### 扫了哪些异常

对照 `GlobalExceptionHandler`、控制器入参、业务 `throw`、JPA `@Version`、文件上传：

| 异常 / 场景 | 结论 |
| --- | --- |
| `HttpRequestMethodNotSupportedException` | 已是 **405 `METHOD_NOT_ALLOWED`**。IT 确认 GET login / GET `/files` / PUT login / DELETE me / PATCH jobs / POST me，不是 500。 |
| `HttpMessageNotReadableException` | 已是 **400 `VALIDATION_FAILED`**。截断 JSON、`[]`/`""`/数字/`true`、trailing comma、未加引号键均非 500。 |
| JSON 类型不匹配（`expectedVersion` 字符串、`hardSkills` 当字符串、`experienceHard` 当对象、版本溢出、`scheduledAt`/`appliedAt` 非法、`confirmationAck`/`sourcedMetric` 非布尔） | 走 Jackson 不可读 → **400**，不是 500。请求 DTO **没有** Jackson 枚举字段。 |
| 业务枚举（面试类型 `NOT_A_ROUND` 等） | 域 `parse()` 抛 `AppException.user`，或 `IllegalArgumentException` 已映射 400。未改语义。 |
| `MethodArgumentTypeMismatchException` / 缺参 / 缺 part / `MultipartException` / `page=abc` | 已是 400。`size=0` / `page=-1` 走 `IllegalArgumentException` → 400。 |
| 乐观锁 | `OptimisticLockingFailureException` / `ObjectOptimisticLockingFailureException` / `OptimisticLockException` 已是 **409 `VERSION_CONFLICT`**。业务 `Versions.assertExpected` 亦 409。`InterviewReviewIT` / `ResumeApplicationIT` 已覆盖。未再把 Hibernate `StaleObjectStateException` 单独映射（Spring Data 会翻译成上述父类；乱加会把真 500 伪装成 4xx）。 |
| 业务 `IllegalStateException`（存储 IO、模拟渠道失败等） | **保持 500**。不是用户改请求能纠正的。 |
| `HttpMediaTypeNotAcceptableException`（`Accept: application/xml`） | **缺口**：落入 `Exception` 兜底，日志 `unhandled error`、分类 `SYSTEM_FAILURE`。写 JSON 失败时客户端有时只看到空 406。 |

未映射（故意）：`BindException` 单独条目（`page=abc` 已是契约 400）；`TypeMismatchException` 父类（会吞掉 `ConversionNotSupportedException`）；`DataIntegrityViolationException`；`HttpMessageNotWritableException`。

### 修了什么

`GlobalExceptionHandler`：`HttpMediaTypeNotAcceptableException` → **406 `NOT_ACCEPTABLE`**（`USER_CORRECTABLE`），并强制 `Content-Type: application/json`，避免错误体再次写失败。不是 400。

`ContractAdversaryIT` 追加扫荡用例 + 405/406 精确断言。

### 未改

- `frontend/` 全部（406 未点名 4xx 已由同事禁止写成系统故障）
- 405 / 400 / 404 / 415 / 409 契约语义
- P0B / 分享入口

### 如何验证

```bash
cd backend
mvn test
```

本轮全量 **121 tests, 0 failures**（原 120 + `ContractAdversaryIT` 1）。未跑 frontend / `npm run build`。

### 下一刀建议

1. 不要开 P0B / 分享入口。不要 git commit，除非调度官另旨。
2. 前端同事继续只改 `frontend/`。

---

## 2026-08-18 晚 · 406 展示补丁官 · NOT_ACCEPTABLE 不再写成系统故障

### 判定

- 后端已将 `Accept: application/xml` 从 500 `SYSTEM_FAILURE` 改为 **406 `NOT_ACCEPTABLE`**（强制 JSON）；`mvn test` 121/0。405 / 413 / 429 已有「这不是系统故障」戳记；406 仍走「其它 4xx」无戳。
- 本刀只改 frontend 共享错误映射与本日志。**未碰 `backend/`**。未重做页面。未开 P0B / 分享入口。未 git commit。

### 映射变化

`frontend/src/shared/api/types.ts` 的 `errorMessage`：

| 条件 | 展示 |
| --- | --- |
| 406 / `NOT_ACCEPTABLE` | 服务端 `message` 或「响应格式不受支持」+ `（406 NOT_ACCEPTABLE。这不是系统故障。）` |
| 405 / 413 / 429 | 保持上一刀戳记 |
| 400 / 404 / 415 | 保持戳记 |
| 其它 4xx | 仍走 `correctableCopy`：服务端文案或「请求无法处理」，**不**写系统故障 / 系统繁忙 / 500 |

另增 `isNotAcceptable`（`status === 406` 或 `reason === 'NOT_ACCEPTABLE'`），与 `isMethodNotAllowed` 同形。

`frontend/src/shared/api/client.ts` 的 `unparsedHttpError`：

- 新增 406 解析：`USER_CORRECTABLE` / `NOT_ACCEPTABLE` / 「响应格式不受支持」，避免无法解析时落到 `SYSTEM_FAILURE` / `BAD_RESPONSE`。
- 其余未点名 4xx 仍收成 `USER_CORRECTABLE` / `CLIENT_ERROR`。5xx 与下载 HTML/空文件仍是系统故障。

### 页面

未改页面。凡调用 `errorMessage()` 的横幅自动吃到 406 戳记。

### 4xx 再扫

- 已点名：400 / 401 / 403 / 404 / 405 / **406** / 409 / 413 / 415 / 422 / 429。
- 未点名 4xx（408 / 410 等）：解析不再是 `SYSTEM_FAILURE`；展示禁止系统故障文案。
- 5xx 仍可写系统繁忙兜底。

### 如何验证

在 `frontend/`：

```bash
npm run build
```

本轮 `vue-tsc -b && vite build` **通过**。

### 未改

- `backend/` 全部
- 页面重做
- 缺口清单全文
- 分享入口 / P0B

### 下一刀建议

1. 不要开 P0B / 分享入口。不要 git commit，除非调度官另旨。

---

## 2026-08-18 晚 · 406 现场复验官 · 重启 8080 后复打 NOT_ACCEPTABLE

### 判定

- 接手时 8080 仍是旧 `JobProofApiApplication`（Java PID 62480，Maven 启动器 49876 / cmd 16700），吃不到上一刀 `HttpMediaTypeNotAcceptableException` → 406 的 handler。
- 只停本项目 8080 树：62480 + 49876 + 16700。未杀 HireProof（8081 / PID 63944）与 5173 Vite。禁止 Python。未 git commit。未开 P0B。
- 现场 **0 条 500**，无需再修 `GlobalExceptionHandler`。**未改** 任何 `backend/` / `frontend/` 源码。

### 重启

- `cd backend` → `mvn -DskipTests spring-boot:run`（dev/H2，slice=S5）。
- 新进程 PID **65168**：`Started JobProofApiApplication`，Tomcat 8080。
- `GET /api/v1/health`（`Accept: application/json`）→ **200**，`status=UP`，`slice=S5`。

### 现场 406 / 抽打（curl，非 Python）

| 用例 | 状态 | reason / 断言 |
| --- | --- | --- |
| GET `/api/v1/health` | 200 | UP / S5，JSON |
| GET `/api/v1/health` + `Accept: application/xml` | **406** | `NOT_ACCEPTABLE`，`Content-Type: application/json`，body 为 JSON，**不是 500** |
| POST `/api/v1/auth/login` 有效账密 + `Accept: application/xml` | **406** | `NOT_ACCEPTABLE`，强制 JSON，**不是 500** |
| GET `/api/v1/me`（已登录）+ `Accept: application/xml` | **406** | `NOT_ACCEPTABLE`，强制 JSON |
| POST login `application/x-www-form-urlencoded` | **415** | `UNSUPPORTED_MEDIA_TYPE` |
| GET `/api/v1/does-not-exist`（已登录） | **404** | `NOT_FOUND` |
| GET `/internal/dev/does-not-exist`（未登录，permitAll） | **404** | `NOT_FOUND` |
| GET `/api/v1/auth/login` | 405 | `METHOD_NOT_ALLOWED`（顺带，非本刀点名） |

空 JSON `{}` + `Accept: application/xml` 登录会先走校验/未认证（当时记到 401）；服务端有一条 WARN：`handleValidation` 在 Accept 不匹配时写 JSON 失败。客户端仍无 500。有效登录与健康检查的 406 已吃到新 handler。

### 改了哪些文件

- 本日志（追加）
- **未改** 任何 `backend/` / `frontend/` 源码

### 如何验证

健康检查：`GET http://127.0.0.1:8080/api/v1/health`（`slice=S5`）。406：同路径加 `Accept: application/xml`。前端：`http://127.0.0.1:5173`（本轮未重启）。

本轮未跑 `mvn test`（现场已 406 且未改代码）。未跑 frontend / `npm run build`。

### 下一刀建议

1. 不要开 P0B / 分享入口。不要 git commit，除非调度官另旨。
2. 前端同事继续只改 `frontend/`。

---

## 2026-08-18 晚 · 406 写响应警告核修官 · handleValidation 强制 JSON

### 判定

- 现场已复现：`POST /api/v1/auth/login` + 空 JSON `{}` + `Accept: application/xml`。
- 先走 `@Valid` → `handleValidation` → 写 JSON 时按客户端 Accept 协商 → **WARN** `Failure in @ExceptionHandler ...#handleValidation`（`HttpMediaTypeNotAcceptableException: No acceptable representation`）。
- 客户端仍无 500；现场落到 **401 `UNAUTHENTICATED`**（写失败后被默认解析/安全入口接走），不是契约 400。
- 有效登录 + Accept xml 仍是 406，上一刀语义不变。本刀只改 backend 与本日志。**未碰 `frontend/`**。未开 P0B / 分享入口。未 git commit。

### 修了什么

`GlobalExceptionHandler`：抽出 `jsonError`，所有错误体强制 `Content-Type: application/json` 再写。

| 场景 | 状态 / reason | 说明 |
| --- | --- | --- |
| 登录 `{}` + Accept xml | **400 `VALIDATION_FAILED`** | 校验先发生，保持用户可纠正，不再因 Accept 写失败 |
| 登录截断 `{` + Accept xml | **400 `VALIDATION_FAILED`** | `handleUnreadable` 同样强制 JSON |
| 健康检查 / 有效登录 + Accept xml | **406 `NOT_ACCEPTABLE`** | 上一刀语义，未改成 400 |
| 未映射系统异常 | **500 `SYSTEM_FAILURE`** | 只强制 JSON，不伪装成 400 |

### 改了哪些文件

- `backend/src/main/java/com/jobproof/infrastructure/web/GlobalExceptionHandler.java`
- `backend/src/test/java/com/jobproof/ContractAdversaryIT.java`（在既有扫荡用例上补 `{}` / `{` + Accept xml）
- 本日志

### 如何验证

```bash
cd backend
mvn test
```

本轮全量 **121 tests, 0 failures**（断言加在既有 `ContractAdversaryIT` 方法内，未增测试方法）。未跑 frontend / `npm run build`。

### 下一刀建议

1. 不要开 P0B / 分享入口。不要 git commit，除非调度官另旨。
2. 前端同事继续只改 `frontend/`。

---

## 2026-08-18 晚 · 前端残留假亮扫官 · 空态吞错与失败假空

### 判定

- P0A 清单 blocker/gap 已清。浏览器点验已修：数据权利/改密未勾选禁用、无法判断 +0。本刀再扫按钮假亮、空态吞错、4xx 当系统故障的残留。
- 只改 `frontend/` 与本日志。**未碰 `backend/`**。未改 `errorMessage` 的 406 戳记。未重做视觉。未开 P0B。未 git commit。

### 查了哪些页

| 页 | 结论 |
| --- | --- |
| `/account` 改密 | 未勾选已 disabled。不改。 |
| `/account/data-rights` 导出/删除/下载 | 勾选闸与 BLOCKING 仍在；导出下载按契约认 `downloadAvailable`/`fileId`/`downloadUrl`。不改。 |
| 简历 PDF 下载 | 无 `fileId` 仍禁用。不改。 |
| 投递详情阶段板 / 面试轮次流转 / 复盘建议 | 非法流转只出 lock-note，不亮前进。撤回/完成复盘未勾选已禁用。不改。 |
| 简历可投递/冻结 | 未确认仍 disabled。不改。 |
| 工作台 | 无写入；失败走 folio fail，不把块失败写成合法空。不改。 |
| 匹配展示 / `?? 0` | matching 无 `totalScore ?? 0`；画像完成度/通知条数的 `?? 0` 不是匹配总分。不改。 |
| `errorMessage` 406 | 只戳 406 / `NOT_ACCEPTABLE`；5xx 仍走系统故障兜底。不改。 |

### 有证据的洞（已修）

列表失败把 `items=[]` 后仍写「空是合法状态 / 还没有数据」：

1. 投递看板、简历列表、通知列表、证据册、面试轮次列表、复盘列表、岗位导入列表、画像/简历候选列表、创建投递下拉：失败时改为 lock-note「读取失败，不能当成没有…」，合法空态只在无 `pageError` 时出现。
2. 通知失败时把 `total` 清 0，避免「共 N 条」与空列表打架。
3. 岗位确认：当前报告非 404 失败不再写「没有当前有效报告」；报告史列表失败单独露出，不再整块消失。
4. 面试/复盘创建：读取中或投递未读到时禁用创建（归档闸在 `app==null` 时曾被当成可写）。
5. 简历关键成果：ACTIVE 证据列表失败时禁用关联与「暂无证据」勾选/提交，避免空下拉被当成没有证据。

### 改了哪些前端文件

- `frontend/src/features/application/pages/ApplicationBoardPage.vue`
- `frontend/src/features/application/pages/ApplicationCreatePage.vue`
- `frontend/src/features/resume/pages/ResumeListPage.vue`
- `frontend/src/features/resume/pages/ResumeEditorPage.vue`
- `frontend/src/features/resume/components/ResumeCandidatePanel.vue`
- `frontend/src/features/notification/pages/NotificationListPage.vue`
- `frontend/src/features/evidence/pages/EvidenceListPage.vue`
- `frontend/src/features/interview/pages/InterviewListPage.vue`
- `frontend/src/features/review/pages/ReviewListPage.vue`
- `frontend/src/features/job/pages/JobImportPage.vue`
- `frontend/src/features/job/pages/JobConfirmPage.vue`
- `frontend/src/features/profile/components/CandidatePanel.vue`
- 本日志

### 未改

- `backend/` 全部
- `frontend/src/shared/api/types.ts` / `client.ts`（406 戳记）
- 缺口清单全文
- 分享入口 / P0B

`npm run build`（`vue-tsc -b && vite build`）**通过**。

### 下一刀建议

1. 不要开 P0B / 分享入口。不要 git commit，除非调度官另旨。

---

## 2026-08-18 晚 · 校验+XML Accept 现场复验官 · 重启 8080 后复打 400/406

### 判定

- 上一刀已把 `GlobalExceptionHandler` 错误体强制 `application/json`（`jsonError`）。接手时 8080 仍是旧 `JobProofApiApplication`（Java PID **65168**，Maven Java 20852 / cmd 57692 / 启动器 38920），吃不到新 handler。
- 只停本项目 8080 树：65168 + 20852 + 57692 + 38920。未杀 HireProof（8081 / PID 54176）与 5173 Vite。禁止 Python。未 git commit。未开 P0B。
- 现场 **0 条 401 / 500**，无需再修 `GlobalExceptionHandler`。**未改** 任何 `backend/` / `frontend/` 源码。未跑 `mvn test`（现场已合契约且未改代码）。

### 重启

- `cd backend` → `mvn -DskipTests spring-boot:run`（dev/H2，slice=S5）。
- 新进程 PID **61576**：`Started JobProofApiApplication`（23:53:27），Tomcat 8080。
- `GET /api/v1/health`（`Accept: application/json`）→ **200**，`status=UP`，`slice=S5`，`Content-Type: application/json`。

### 现场复打（curl.exe，非 Python）

| 用例 | 状态 | reason / 断言 |
| --- | --- | --- |
| GET `/api/v1/health` | **200** | UP / S5，JSON |
| POST `/api/v1/auth/login` `{}` + `Accept: application/xml` | **400** | `VALIDATION_FAILED`，`Content-Type: application/json`，**不是 401/500** |
| POST `/api/v1/auth/login` `{` + `Accept: application/xml` | **400** | `VALIDATION_FAILED`，JSON，**不是 401/500** |
| GET `/api/v1/health` + `Accept: application/xml` | **406** | `NOT_ACCEPTABLE`，强制 JSON，**不是 500** |
| POST 有效登录 + `Accept: application/xml` | **406** | `NOT_ACCEPTABLE`，强制 JSON，**不是 500** |
| POST login `application/x-www-form-urlencoded` | **415** | `UNSUPPORTED_MEDIA_TYPE`，JSON |

新进程日志无 `Failure in @ExceptionHandler ...#handleValidation`，无 `unhandled error` / `SYSTEM_FAILURE`。

### 改了哪些文件

- 本日志（追加）
- **未改** 任何 `backend/` / `frontend/` 源码

### 如何验证

健康检查：`GET http://127.0.0.1:8080/api/v1/health`（`slice=S5`）。登录空体：`POST /api/v1/auth/login` + `{}` + `Accept: application/xml` 应为 400 `VALIDATION_FAILED`。纯 Accept 不匹配：同路径健康检查加 xml 应为 406。前端：`http://127.0.0.1:5173`（本轮未重启）。

本轮未跑 `mvn test`（现场已合契约且未改代码）。未跑 frontend / `npm run build`。

### 下一刀建议

1. 不要开 P0B / 分享入口。不要 git commit，除非调度官另旨。
2. 前端同事继续只改 `frontend/`。

---

## 2026-08-18 晚 · 空列表文案核修官 · 成功空不得写成读取失败

### 判定

上一刀已把「列表失败伪装成还没有数据」改成 lock-note。本刀核：读取成功且长度为 0 仍是契约允许的合法空（0 证据 / 0 轮面试 / 0 复盘 / 0 可绑定版本）。只改 `frontend/` 文案与条件判错。**未改** `backend/`、**未改** `errorMessage`。未开 P0B。未 git commit。

### 各页：成功空 vs 失败

| 页 | 成功空（length 0） | 失败 | 本刀 |
| --- | --- | --- | --- |
| 投递看板 | 「尚无投递。空是合法状态。」 | lock-note「投递列表读取失败，不能当成尚无投递。」 | 未改。`pageError` 只来自列表读取。 |
| 通知列表 | 「还没有站内通知。空是合法状态。」 | lock-note「通知读取失败，不能当成没有通知。」 | 未改。写操作用 `formError`。 |
| 简历关键成果 | 「还没有关键成果。空列表可以通过冻结闸」 | 证据列表失败单独 lock-note，不改成果空态 | 未改。 |
| 岗位确认 · 当前报告 | 「没有当前有效报告。」`MATCH_REPORT_NOT_FOUND` 不当失败 | 非 404 才 `currentReportFailed` | 未改。404 与列表失败已分开。 |
| 岗位确认 · 报告史 | 成功 0 条不展示史块 | `reportsListFailed` 才 lock-note | 未改。 |
| 创建投递下拉 | 一侧成功 0 条仍「没有可绑定 / 没有已确认」 | 仅该侧 `*ListFailed` 才 lock-note | **已改**。原共用 `pageError`，一侧失败会把另一侧合法空写成读取失败。 |
| 岗位导入列表 | 「尚无导入岗位。空是合法状态。」 | 仅 `jobsListFailed` | **已改**。任务读取失败不再把合法空岗位列表写成「岗位列表读取失败」。 |
| 面试轮次 | 「还没有轮次。0 轮合法。」 | 仅 `listFailed`：「面试轮次读取失败，不能当成没有轮次。」 | **已改**。投递 404 / 投递读取失败不再写成轮次列表失败；也不再显示 0 轮合法。 |
| 复盘列表 | 「还没有复盘。」 | 仅 `listFailed` | **已改**。同上拆投递对象失败 vs 复盘列表失败；轮次下拉失败单独 lock-note，不当成「没有可关联轮次」。 |
| 岗位确认 · 岗位本身 | 有 job 无版本：「没有岗位版本。」 | 岗位 404 / 读取失败：「岗位未读到，不能当成没有岗位版本。」 | **已改**。对象不存在不再写成合法空版本。 |
| 画像 / 简历候选 | 筛选空 hint | 仅 `listFailed` | **已改**。写操作 `pageError` 不再把成功空写成「候选列表读取失败」。 |
| 证据册 / 简历主档列表 | 「尚未登记 / 此筛选下没有」 | 仅 `listFailed` | **已改**。写操作失败不再冒充列表读取失败。 |
| 面试 / 复盘创建禁用 | 成功 0 轮仍可创建（未归档） | 投递未读到才禁用；列表失败不把 app 清掉 | **已改**。不再因轮次列表失败而假装投递不存在。 |

### 改了哪些前端文件

- `frontend/src/features/application/pages/ApplicationCreatePage.vue`
- `frontend/src/features/job/pages/JobImportPage.vue`
- `frontend/src/features/job/pages/JobConfirmPage.vue`
- `frontend/src/features/interview/pages/InterviewListPage.vue`
- `frontend/src/features/review/pages/ReviewListPage.vue`
- `frontend/src/features/profile/components/CandidatePanel.vue`
- `frontend/src/features/resume/components/ResumeCandidatePanel.vue`
- `frontend/src/features/evidence/pages/EvidenceListPage.vue`
- `frontend/src/features/resume/pages/ResumeListPage.vue`
- 本日志

### 未改

- `backend/` 全部
- `frontend/src/shared/api/types.ts` 的 `errorMessage`
- 投递看板、通知列表、简历关键成果、岗位确认当前报告 / 报告史的空态文案（核过，无必须改）
- 分享入口 / P0B

`npm run build`（`vue-tsc -b && vite build`）**通过**。

### 下一刀建议

1. 不要开 P0B / 分享入口。不要 git commit，除非调度官另旨。

---

## 2026-08-19 凌晨 · 业务契约残留官 · 语义闸复扫

### 判定

- 对照冻结契约 v0.7：未确认 AI、匹配自动建投递 / 无法判断人造总分、面试复盘回写投递、归档后改阶段、对象删除变整号、通知失败回滚主事务、越权读文件/任务/导出。
- 4xx/406/强制 JSON 已收口；本刀 **不改** `GlobalExceptionHandler`。并行同事只改 frontend；本刀只核 backend。
- **扫过，无必须改。**

### 核了哪些（有闸，未复现可修洞）

| 契约点 | 核到 | 结论 |
| --- | --- | --- |
| 未确认 AI 当正式事实 / 可投递 / 冻结 | `ResumeFreezePolicy` `UNCONFIRMED_AI_FACTS`；导入只进候选；定制成功仅为 `PENDING_USER_CONFIRMATION`；画像 matchingRead 只吃正式事实 | 核过，不改 |
| 匹配自动建投递 | `MatchingService` 无 `ApplicationService.prepare/record`；`JobMatchingContractIT` 无法判断后投递列表 0 | 核过，不改 |
| 无法判断人造总分 | `MatchScoringEngine` 命中 4.3.13 时 `total=null`；报告 JSON / 工作台省略 `totalScore` | 核过，不改 |
| 面试/复盘回写投递 | `InterviewService`/`ReviewService` 只 `requireOwnRef`/`requireWritableApplication`；改结果/完成不 `advance` 投递 | 核过，不改 |
| 归档后改阶段 | `assertStageWritable` → 409 `APPLICATION_ALREADY_ARCHIVED`；归档后轮次/复盘写同样拒绝 | 核过，不改 |
| 对象删除变整号 | `DeletionOrchestrator` OBJECT 走 `processObject`，不 `markDeletionPending`；`ObjectDeletionIT` 删投递后账号仍 ACTIVE 可登录 | 核过，不改 |
| 通知失败回滚主事务 | `NotificationService.request` 独立事务；投递前进 `failNextWrite` 后仍 SUBMITTED | 核过，不改 |
| 越权读文件/任务/导出 | `getOwned`/`downloadOwned`/`requireExport`；`ContractAdversaryIT` 他人 403 | 核过，不改 |

默认未铺 Resume `@Version`、未删 `APPLICATION_STAGE`、未关 dev mailbox、未做分享入口。

### 改了哪些文件

- 本日志（追加）
- **未改** 任何 `backend/` / `frontend/` 源码
- **未碰** `GlobalExceptionHandler`

### 如何验证

```bash
cd backend
mvn test
```

本轮全量 **121 tests, 0 failures**。未跑 frontend / `npm run build`。未 git commit。未用 Python。

### 下一刀建议

1. 不要开 P0B / 分享入口。不要 git commit，除非调度官另旨。
2. 前端同事继续只改 `frontend/`。

---

## 2026-08-19 凌晨 · 空态点验官 · 成功空 vs 404 vs 有数据

### 判定

- 8080 `GET /api/v1/health` → 200，`slice=S5`。5173 Vite 仍在。未改 `backend/`。未开 P0B。未 git commit。
- 新账号会话：工作台十块 `ok:true` 空数据；证据 `items=[]`；投递 `[]`。投递对象不存在走 `400 APPLICATION_NOT_FOUND`（前端 `isNotFound` 认 reason），不是列表 5xx。
- 有数据列表（本账号补 1 条证据 + 1 份简历后）**没有**被 lock-note 误伤。工作台按钮只有「重新读取 / 退出」，无建轮次 / 复盘 / 冻结。

### 点了哪些、通/断

| 页 | 结论 |
| --- | --- |
| `/workspace` 空账号 | **通**。面试块「0 轮是合法空」。无「读取失败」。无 lock-note。无写入。 |
| `/evidences` 空 | **通**。「尚未登记证据。空是合法状态。」无「读取失败」。 |
| `/evidences` 有 1 条 | **通**。出卡片，无 lock-note，无「读取失败」。 |
| `/applications` 空 | **通**。「尚无投递。空是合法状态。」 |
| `/applications/new` 空下拉 | **通**。「没有可绑定 / 没有已确认」。无读取失败 lock-note。 |
| `/jobs` `/resumes` 空 | **通**。合法空。简历有 1 条后只出主档行，无 lock-note。 |
| `/notifications` `/profile` 空 | **通**。合法空；候选「没有待处理」。无「读取失败」。 |
| 投递不存在 → `/interviews` | **先断后通**。列表已写「投递未读到」，但页头仍写「0 轮是合法空」，与「不能当成 0 轮」打架。 |
| 投递不存在 → `/reviews` | **通**。「投递未读到」。无「0 轮合法」。 |
| `/jobs/{不存在}` | **通**。「岗位未读到」。无「没有岗位版本」。 |

### 改了什么

- `frontend/src/features/interview/pages/InterviewListPage.vue`：页头「0 轮是合法空」仅在投递对象已读到（`app`）时出现。404/未读到只保留「投递未读到」lock-note。
- 本日志

修后再点面试 404：页头不再写 0 轮合法；lock-note「投递未读到」仍在；无「读取失败」。

### 未改

- `backend/` 全部
- 其它列表页条件（点验已合上一刀拆法）
- 分享入口 / P0B

`npm run build`（`vue-tsc -b && vite build`）**通过**。

### 下一刀建议

1. 不要开 P0B / 分享入口。不要 git commit，除非调度官另旨。

---

## 2026-08-19 凌晨 · 主路径抽打官 · 抽打通过

### 判定

- 复用现场 **8080**（`GET /api/v1/health` → 200，`status=UP`，`slice=S5`）与 **5173** Vite（`/` 与 `/login` 200），未重启。禁止 Python。未 git commit。未开 P0B / 分享入口。并行同事只改 frontend 401 跳转；本刀 **未碰 `frontend/`**，**未改 `backend/`**。
- 对照冻结契约关键闸与 95/0 复冒烟：无法判断无总分、未确认不能冻、归档不能改阶段、对象删除不整号、PDF `%PDF-`、空 JSON + xml Accept 仍 400。现场再打均通。
- **无产品断点。抽打通过。** 只追加本日志。

### 跑了什么

1. Node HTTP 整链（非 Python）：注册→登录→档案→证据→JD→匹配→简历冻结→投递→面试轮次→复盘→工作台→数据权利导出。
2. 现场 **100 PASS / 0 产品 FAIL / 1 SKIP**。较 95/0 多出本刀补打的 Accept 闸（健康检查 xml 406、空/截断 JSON + xml 仍 400、有效登录 + xml 406）。
3. **未改后端，未跑** `mvn test`。上一刀业务契约残留官记全量 **121 / 0**。
4. 未改前端，**未跑** `npm run build`。

### 主路径通/断

| 路径 | 结果 |
| --- | --- |
| 8080 健康 / 5173 登录与工作台等页 | 通 |
| 空 JSON `{}` + `Accept: application/xml` 登录 | 通：400 `VALIDATION_FAILED`，强制 JSON，不是 401/500 |
| 截断 `{` + xml Accept 登录 | 通：400 `VALIDATION_FAILED` |
| 健康检查 / 有效登录 + xml Accept | 通：406 `NOT_ACCEPTABLE`，强制 JSON，不是 500 |
| 注册 / 登录 / 会话 / 登出 / 再登 | 通 |
| 未确认方向不能匹配 | 通 `DIRECTION_NOT_CONFIRMED` |
| 档案 / 证据 / JD | 通 |
| 4.3.13 无法判断（≥4 未知）`unableToJudge=true`，无 `totalScore` | 通 |
| 新边：方向已确认、未知不足 4、空加权分母；`unableToJudge=false`，等级仍「无法判断」，无人造总分/0 | 通 |
| 有对照后真实打分有总分；匹配不自动建投递 | 通 |
| 未确认 AI 不能 ready/冻结 | 通 `UNCONFIRMED_AI_FACTS` |
| 冻结后 PDF：本人 `%PDF-`（8080 与 5173 代理），他人 403 JSON，未登录 401 JSON，非 HTML | 通 |
| 投递绑冻结版本 + CONFIRMED；未确认岗 409；归档后不能改阶段 | 通 `APPLICATION_ALREADY_ARCHIVED` |
| 进入面试中不自动建轮次；显式建轮次不回写投递 | 通 |
| 复盘建议未确认不当正式弱项；复盘不改投递阶段 | 通 |
| 工作台十块只读；POST/PUT 405；href 映射实页；无法判断藏分 | 通 |
| 导出 JSON 以 `{` 开头，不是 HTML/`%PDF-`；未登录 401 JSON | 通 |
| 账号删除无 ack 400；二次确认后 SUBMITTED | 通 |
| 对象预览 `scope=OBJECT` 有 `impacts`；引用证据 BLOCKING 不能提交；成功后账号仍 ACTIVE、兄弟对象仍在 | 通 |
| 空/表单 POST 不是 500 | 通 |
| SEND_FAILED 不能标已读 | 现场无失败样本 SKIP；IT 仍由 `NotificationChannelFailureIT` 覆盖 |

### 改了哪些文件

- 本日志（追加「抽打通过」）
- **未改** 任何 `backend/` / `frontend/` 源码
- **未碰** 前端 401 路由

### 如何验证

健康检查：`GET http://127.0.0.1:8080/api/v1/health`（`slice=S5`）。前端：`http://127.0.0.1:5173`。空 JSON + xml：`POST /api/v1/auth/login` body `{}` + `Accept: application/xml` 应为 400。

### 下一刀建议

1. 不要开 P0B / 分享入口。不要 git commit，除非调度官另旨。
2. 前端同事继续只改 `frontend/` 401 跳转。

---

## 2026-08-19 凌晨 · 401 会话跳转核修官 · 核过，无必须改

### 判定

- 只核 `frontend/` 会话跳转：未登录进需登录页、`safeNextPath`、登录成功 `next`、API 401 回登录。**未改** `frontend/` 源码，**未改** `backend/`，未开 P0B，未 git commit。
- **核过，无必须改。** 现网链路已满足：`/account/data-rights` 未登录 → `/login?reason=unauthenticated&next=...`；登录成功只走 `safeNextPath`；会话 401 清账号并 `replace` 登录，不把 401 当系统故障死在业务页。未削弱 `safeNextPath`。

### 核了哪些路径

| 点 | 核到 | 结论 |
| --- | --- | --- |
| 未登录进 `/account/data-rights` | `router.ts` `requiresAuth` + `beforeEach`：`next: to.fullPath`，`reason=unauthenticated`（无 `reason` 时） | 通。Vue 已注册路由的 `fullPath` 为站内路径；登录成功再过 `safeNextPath` |
| 其它需登录页 | `/workspace` `/profile` `/evidences` `/jobs` `/matching/*` `/resumes` `/applications` `/interviews` `/reviews` `/notifications` `/account` 皆 `meta.requiresAuth` | 通。同一守卫 |
| `next` 安全 | `safeNext.ts`：拒 `https://`、`//`、`\\`、控制符/空白、`://`、origin 漂移、`/login` `/register` `/reset` `/` 循环；`/profile/../login` 规范化后回落工作台；`/profile`、`/account/data-rights` 放行 | 通。未改此函数 |
| 登录成功只跳安全 `next` | `LoginPage.vue` `router.replace(safeNextPath(route.query.next))`；数组/`next` 非字符串回落 `/workspace` | 通 |
| 已登录访登录页 | `guestOnly` → `workspace`（无 `next`）。不是开放重定向 | 核过，不改 |
| API 401 会话过期 | `client.ts` `api`/`apiDownload` 调 `onUnauthenticated`；`bindSessionExpiry` 仅在内存里已有账号时清会话并 `replace` 登录（`reason=session_expired` + `safeNextPath(fullPath)`） | 通。登录页错密 401 时 `account` 为空，不误跳 |
| 401 不当系统故障横幅 | `hydrateSession` 401 不写 `bootError`；工作台 / 通知旁路 / 退出已 `isUnauthenticated` 吞横幅；其它业务页 catch 会闪一句，但 handler 已 `replace` 登录，不是死在业务页当 500 | 核过，不改（避免与并行抽打抢大面积页面） |
| `/login?next=//evil.com` 等 | 登录成功 `safeNextPath` 回落 `/workspace` | 通 |

三重编码残留 `/%2F%2F…`：`router.replace` 仍拼到本源 `history`，catch-all 回 `/`，不是外链。不改 `safeNextPath`，以免削弱现网闸。

### 改了哪些文件

- 本日志（追加「核过，无必须改」）
- **未改** 任何 `frontend/src/**` / `backend/` 源码
- 探测用临时脚本已删，未留在仓库

### 如何验证

```bash
cd frontend
npm run build
```

本轮 `vue-tsc -b && vite build` **通过**（exit 0）。未 git commit。未用 Python。

### 下一刀建议

1. 不要开 P0B / 分享入口。不要 git commit，除非调度官另旨。
2. 其它前端刀不要放松 `safeNextPath`；401 回登录继续走 `bindSessionExpiry`，不要改成业务页系统故障横幅。

---

## 2026-08-19 凌晨 · 前端注入展示核修官 · 指定面无 v-html；数据权利 href 已闸

### 判定

- 只改 `frontend/`。**未改** `backend/`，未开 P0B，未 git commit，未做富文本编辑器。
- **档案 / JD / 匹配解释 / 简历 / 通知 / 复盘建议：扫过，无必须改。** 全 frontend/src 无 `v-html` / `innerHTML` / `document.write`。上述用户/AI 字符串均走 Vue `{{ }}` 文本插值（通知 `title`/`body`、匹配 `name`/`basis`/扣分缺失、复盘 `inputText`/`suggestions[].text`、JD/简历/画像为 textarea 或 `pre` 文本）。服务端字符串不当 HTML。
- **必须改一处：未校验 href。** `DataRightsPage` 曾对非 `/` 的 `downloadUrl` 以及 `signed.url` 直接 `window.open`。本地存储签名是 `local://…`，恶意串可以是 `javascript:`。简历 PDF 下载早已只走会话 `/api/v1/files/{id}/download`。本刀对齐该闸。

### 搜到哪些点

| 点 | 核到 | 结论 |
| --- | --- | --- |
| `v-html` / `innerHTML` / `document.write` | `frontend/src` 零命中 | 无必须改 |
| 档案 | `ProfilePage` 正式事实、`CandidatePanel` `prettyValue` → `{{ }}` / `pre` | 扫过，无必须改 |
| JD | `JobImportPage` textarea；`JobConfirmPage` 七项 input/textarea，`{{ title }}` | 扫过，无必须改 |
| 匹配解释 | `ExplainPanel` `item.name`/`basis`/`deductions`/`missingItems`/`unknownItems` 皆 `{{ }}` | 扫过，无必须改 |
| 简历 | `ResumeEditorPage` textarea + `pre` 快照；PDF 已用 blob + 会话 path | 扫过，无必须改 |
| 通知 | `NotificationListPage` `{{ item.title }}` `{{ item.body }}`；`AppBanner` 只 slot 文本 | 扫过，无必须改 |
| 复盘建议 | `ReviewDetailPage` `{{ review.inputText }}` `{{ item.text }}` 问题/回答列表 | 扫过，无必须改 |
| 工作台 href | `SourcePath` 把契约 href 画在 `<code>`；`RouterLink :to` 只吃 `frontendPathForApi` 白名单 | 扫过，无必须改 |
| 登录 `next` | `safeNextPath` 已拒外链 / `javascript:` 类 scheme | 未改（并行 Cookie 刀范围外） |
| 证据可核验链接 | `type="url"` 输入，不当 `<a :href>` | 扫过，无必须改 |
| 数据权利 `window.open(downloadUrl / signed.url)` | 未校验协议 | **已改** |

### 改了哪些文件

- `frontend/src/features/datarights/services/dataRightsApi.ts`：`isSessionApiDownloadPath`、`isSafeHttpDownloadUrl`；`downloadExportByPath` 走前者。
- `frontend/src/features/datarights/pages/DataRightsPage.vue`：下载顺序改为会话 `/api` → `fileId` 会话下载 → 导出接口 → 仅 http(s) 才 `window.open`；拒 `javascript:` / `data:` / `local:`。
- 本日志。

### 如何验证

```bash
cd frontend
npm run build
```

本轮 `vue-tsc -b && vite build` **通过**（exit 0）。未 git commit。未用 Python。

---

## 2026-08-19 凌晨 · 上传闸现场复验官 · 重启 8080 后复打；超限码最小修

### 判定

- `FileUploadPolicy` 已补：非空、≤1MB、只收 PDF/PNG/JPEG/WebP、魔数须对上扩展名、文件名只取 basename、key 账号隔离。接手时 8080 仍是 **00:46 旧进程**（`JobProofApiApplication` PID **28172**），class 于 00:48 才编出，吃不到闸。
- 只停本项目 8080 树：28172 + Maven Java 42344 + `mvn.cmd` 55140。未杀 HireProof（8081 / PID 54480）与 5173 Vite。禁止 Python。未 git commit。未开 P0B。未改 frontend / Cookie / `GlobalExceptionHandler`。
- 第一次重启后现场：合法 PNG / 空文件 / 非白名单扩展名 / 他人读均按闸。**略大于 1MB 的允许类型**被 Spring 默认 multipart 1MB 先拦，回 400 `VALIDATION_FAILED`，不是 `FILE_TOO_LARGE`。现场闸这一条未生效。
- 最小修：`application.yml` 把 `spring.servlet.multipart.max-file-size` 调到 **2MB**（仍低于默认 `max-request-size` 10MB），让略超 1MB 的附件落到 `FileUploadPolicy` 的 `FILE_TOO_LARGE`。不改 handler。第二次重启后该条对齐。

### 本轮做了什么

1. 停旧 8080 后 `cd backend` → `mvn -DskipTests spring-boot:run`（dev/H2，slice=S5）。第一轮 PID **61528**（00:53:47）。
2. 登录后复打上传闸；发现超限码被容器默认 1MB 收成 `VALIDATION_FAILED`。
3. 只改 `backend/src/main/resources/application.yml` 的 multipart 上限。再停 61528 + Maven 51412 + cmd 57636，重启 PID **44884**（00:56:03）。
4. 复打全表通过。`mvn test`：**131 tests, 0 failures**。

### 主路径通/断

| 路径 | 状态 | reason |
| --- | --- | --- |
| `GET /api/v1/health` | **200** | `status=UP`，`slice=S5` |
| 合法小 PNG 上传 | **200** | 有 `data.id`（例 `517915c5-…`），`filename=ok.png` |
| 空文件上传 | **400** | `FILE_INVALID` |
| 略大于 1MB 的 PNG（修前 / 第一轮 8080） | **400** | `VALIDATION_FAILED`（闸未吃到） |
| 略大于 1MB 的 PNG（修后 / PID 44884） | **400** | `FILE_TOO_LARGE` |
| 扩展名不在白名单（`.exe` / `.html`） | **400** | `FILE_TYPE_NOT_ALLOWED`，**不是 500** |
| 他人 `fileId` `GET .../download` | **403** | `OBJECT_FORBIDDEN` |
| 他人 `fileId` `GET .../download-url` | **403** | `OBJECT_FORBIDDEN` |

### 改了哪些文件

- `backend/src/main/resources/application.yml`：`spring.servlet.multipart.max-file-size: 2MB`
- 本日志
- **未改** `FileUploadPolicy` / `FileAccessService` / `FileController` / handler / Cookie / frontend

### 如何验证

健康检查：`GET http://127.0.0.1:8080/api/v1/health`（`slice=S5`）。登录后对 `POST /api/v1/files` 打合法小 PNG、空文件、略大于 1MB 的允许类型、非白名单扩展名；他人会话读已上传 `fileId`。在 `backend/`：`mvn test`。

### 下一刀建议

1. 不要开 P0B / 分享入口。不要 git commit，除非调度官另旨。
2. 前端同事继续只改证据文案；不要动 Cookie / handler。
3. 现场 8080 已吃到闸（PID **44884**）；后续复验可复用，勿再误杀无关 Java。

---

## 2026-08-19 凌晨 · 重置码现场复验官 · 重启 8080 后复打闸门

### 判定

- 上一刀已落地：重发/成功 `consumeAll`、过期即消费、5 次错误锁定、对外只报 `RESET_CODE_INVALID`、未知邮箱不暴露；Flyway `V6__password_reset_failed_attempts.sql` 加 `failed_attempts`。接手时 8080 仍是 **00:56 旧进程**（`JobProofApiApplication` PID **44884**），Flyway 停在 **v5**（当时只校验 5 条 migration），吃不到 V6。
- 只停本项目 8080 树：44884 + Maven Java 54020 + `mvn.cmd` 26668。未杀 HireProof（8081 / PID 48660）与 5173 Vite。禁止 Python。未 git commit。未开 P0B。**未改 `backend/` 源码**（闸已生效）。未改 frontend（并行同事改重置页文案）。
- 新进程 PID **59596**：`Started JobProofApiApplication`（01:15:12）。Flyway：**Successfully validated 6 migrations**，**applied 1 migration，now at version v6**。
- 本机 curl/HTTP（dev mailbox 仅本机取码）复打全表通过，现场闸生效，故不修代码、不跑 `mvn test`。

### 本轮做了什么

1. 停旧 8080 后 `cd backend` → `mvn -DskipTests spring-boot:run`（dev/H2，slice=S5）。
2. `GET /api/v1/health` 与 `/actuator/health` 均为 UP。
3. 按闸复打：未知邮箱 request、已知邮箱重发旧码、5 错锁定后第 6 次、弱新密保码与会话、成功后旧会话 401。

### 主路径通/断

| 路径 | 状态 | reason / 形态 |
| --- | --- | --- |
| `GET /api/v1/health` | **200** | `status=UP`，`slice=S5` |
| `GET /actuator/health` | **200** | `{"status":"UP"}` |
| 未知邮箱 `POST .../reset/request` | **200** | `{"ok":true}`，无 `data`，正文不暴露是否存在 |
| 未知邮箱 `GET /internal/dev/mailbox/{email}` | **400** | `MAIL_NOT_FOUND`（仅 dev 信箱，不经 request 接口） |
| 未知邮箱 `POST .../reset/confirm` | **400** | `RESET_CODE_INVALID` |
| 已知邮箱 request（两次） | **200** | 形态与未知邮箱相同 `{"ok":true}`；两次 mailbox 码长度 6 且不同 |
| 重发后再用旧码 confirm | **400** | `RESET_CODE_INVALID` |
| 新码 confirm 成功 | **200** | `{"ok":true}` |
| 已成功码再 confirm | **400** | `RESET_CODE_INVALID` |
| 错误码 5 次 confirm | **400×5** | 皆 `RESET_CODE_INVALID` |
| 锁定后第 6 次用正确码 | **400** | 仍只报 `RESET_CODE_INVALID`；原密码登录仍 200 |
| 弱新密 confirm | **400** | `PASSWORD_TOO_WEAK`；`GET /me` 旧会话仍 200（原码仍可用） |
| 正确码成功后 `GET /me` | **401** | `UNAUTHENTICATED` |

### 改了哪些文件

- 本日志
- **未改** `backend/` 源码、frontend、Cookie、handler

### 如何验证

健康检查：`GET http://127.0.0.1:8080/api/v1/health`（`slice=S5`）。重置：`POST /api/v1/auth/password/reset/request|confirm`；本机取码：`GET /internal/dev/mailbox/{email}`（仅 dev）。成功后旧 Cookie 打 `GET /api/v1/me` 应为 401。

### 下一刀建议

1. 不要开 P0B / 分享入口。不要 git commit，除非调度官另旨。
2. 前端同事继续只改重置页文案；不要动 Cookie / handler / 重置码策略。
3. 现场 8080 已吃到 V6 闸（PID **59596**）；后续复验可复用，勿再误杀无关 Java。

---

## 2026-08-19 凌晨 · 并发注册现场复验官 · 重启 8080 后复打 uk_accounts_email

### 判定

- 上一刀已把 `register` 对 `uk_accounts_email` 收成 409 `EMAIL_ALREADY_REGISTERED`（仅认该约束，其它唯一冲突不伪装成 409）。`mvn test` 已报 160/0。接手时 8080 仍是 **01:15 旧进程**（`JobProofApiApplication` PID **59596**），吃不到新 class。
- 只停本项目 8080 树：59596 + Maven Java **58752** + `cmd` **9672**。未杀 HireProof（8081 / PID **61436**）与 5173 Vite。禁止 Python。未 git commit。未开 P0B。**未改 `backend/` 源码**（现场已不是 500）。未改 frontend（并行同事核登出）。
- 新进程 PID **46560**：`Started JobProofApiApplication`（01:45:38），Tomcat 8080。Flyway：**Successfully validated 6 migrations**。dev/H2。
- 同一新邮箱两路并行 `POST /api/v1/auth/register`：**409 + 200**，随后顺序再打 **409**，**没有 500**。Hibernate 仍会打 `SqlExceptionHelper` ERROR（`23505` / `uk_accounts_email`），但 HTTP 已收成冲突码。

### 本轮做了什么

1. 停旧 8080 后 `cd backend` → `mvn -DskipTests spring-boot:run`（dev/H2，slice=S5）。
2. `GET /api/v1/health` 与 `/actuator/health` 均为 UP。
3. 邮箱 `race-p0a-1787075171733@example.com`，两路并行 POST register，再打一路顺序重复注册。

### 主路径通/断

| 路径 | 状态 | reason / 形态 |
| --- | --- | --- |
| `GET /api/v1/health` | **200** | `status=UP`，`slice=S5` |
| `GET /actuator/health` | **200** | `{"status":"UP"}` |
| 并行 A `POST /api/v1/auth/register` | **409** | `EMAIL_ALREADY_REGISTERED` / `CONFLICT` / 「该邮箱已注册」 |
| 并行 B 同邮箱同体 | **200** | 账号 `ACTIVE` / `SEEKER` |
| 顺序第三路同邮箱 | **409** | `EMAIL_ALREADY_REGISTERED`，**不是 500** |

### 改了哪些文件

- 本日志
- **未改** `backend/` 源码、frontend、Cookie、handler；其它无关唯一冲突未改成 409

### 如何验证

健康检查：`GET http://127.0.0.1:8080/api/v1/health`（`slice=S5`）。并发：同一新邮箱两路同时 `POST /api/v1/auth/register`（`{"email","password":"Passw0rd!"}`），应 1 个 200、另 1 个 409（或顺序 409），不要 500。

### 下一刀建议

1. 不要开 P0B / 分享入口。不要 git commit，除非调度官另旨。
2. 并行同事继续核登出；不要动 Cookie / handler / 注册唯一约束映射。
3. 现场 8080 已吃到邮箱冲突闸（PID **46560**）；后续复验可复用，勿再误杀无关 Java。

