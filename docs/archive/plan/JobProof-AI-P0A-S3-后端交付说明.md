# JobProof AI P0A S3 后端交付说明

> 日期：2026-08-18
> 角色：S3 后端切片官（投递使）
> 编码：UTF-8
> 性质：本刀可运行 API + 契约说明 + 验收门对照。**不是**「S3 前端可开工」宣布；调度官仍需走交接仪式。未开 S4 面试复盘，未写 Vue / 正式 Mock，未 git commit。Matching 打分未改。

## 1. 本切片交付了什么

同一 Spring Boot 单体新增包：`modules.resume`、`modules.application`。Flyway `V4__s3_resume_application.sql` 只加主档、简历候选、简历版本、投递、阶段时间线。冻结引用复用 S1 `evidence_references`，调用 `EvidenceService.registerActiveReference`。**不建**收藏、漏斗、面试轮次表。S0–S2 未推翻。

AI 只出候选。定制任务成功得到 `PENDING_USER_CONFIRMATION`，不得直接变成已冻结。创建投递只引用 `FROZEN`/`BOUND` 简历版本与 `CONFIRMED` 岗位版本；匹配报告不是前置。Matching 不得改投递。

### 已实现命令 / 查询

| 能力 | 方法 | 路径 | 权限 |
| --- | --- | --- | --- |
| 创建主档（空白 / 模板 / 导入） | POST | `/api/v1/resumes` | 本人；运营禁止看原文。体：`mode=BLANK\|TEMPLATE\|IMPORT` |
| 主档列表 | GET | `/api/v1/resumes` | 本人 |
| 主档详情（含版本） | GET | `/api/v1/resumes/{id}` | 对象级本人 |
| 编辑正式字段 / 关键成果 | PUT | `/api/v1/resumes/{id}` | 本人；`expectedVersion` 可选 |
| 复制主档 | POST | `/api/v1/resumes/{id}/copy` | 本人；新草稿，不复制投递 |
| 归档 / 恢复主档 | POST | `/api/v1/resumes/{id}/archive`、`/restore` | 本人；恢复为归档前状态 |
| 标可投递 | POST | `/api/v1/resumes/{id}/ready` | 本人；须无未确认 AI，关键成果已处理 |
| 登记 AI 候选 | POST | `/api/v1/resumes/{id}/candidates` | 本人；主档变 `PENDING_CONFIRMATION` |
| 候选列表 | GET | `/api/v1/resumes/{id}/candidates` | 本人 |
| 确认 / 拒绝 / 更正候选 | POST | `/api/v1/resumes/{id}/candidates/{cid}/confirm`、`/reject`、`/correct` | 本人 |
| 关联证据 | POST | `/api/v1/resumes/{id}/outcomes/{oid}/evidence` | 本人；仅 ACTIVE 证据 |
| 逐条确认暂无证据仍要冻结 | POST | `/api/v1/resumes/{id}/outcomes/{oid}/waive` | 本人；须 `confirmed=true` |
| 从主档冻结版本 | POST | `/api/v1/resumes/{id}/freeze` | 本人；仅 `READY_TO_APPLY`；失败不留半成品 |
| 版本列表 / 详情 | GET | `/api/v1/resumes/{id}/versions`、`/api/v1/resumes/versions/{versionId}` | 本人 |
| 确认定制版本并冻结 | POST | `/api/v1/resumes/versions/{versionId}/confirm` | 本人；不得由任务自动冻结 |
| 归档版本 | POST | `/api/v1/resumes/versions/{versionId}/archive` | 本人；不解除已绑定投递 |
| 对比两版本 | GET | `/api/v1/resumes/versions/{id}/compare?with=` | 本人 |
| 导出 PDF 任务 | POST | `/api/v1/resumes/versions/{id}/export-pdf` | 本人；仅已冻结 / 已绑定 |
| 按已确认 JD 定制 | POST | `/api/v1/resumes/{id}/customize` | 本人；须 `CONFIRMED` 岗位版本 |
| 取消 / 重试定制 | POST | `/api/v1/resumes/customize-tasks/{taskId}/cancel`、`/retry` | 按任务五态 |
| 准备投递 | POST | `/api/v1/applications/prepare` | 本人；建 `PENDING_SUBMIT` |
| 记录已投递 | POST | `/api/v1/applications/record` | 本人；建 `SUBMITTED` |
| 投递列表 / 详情 | GET | `/api/v1/applications`、`/api/v1/applications/{id}` | 本人 |
| 改渠道 / 备注 / 备注公司名 | PUT | `/api/v1/applications/{id}` | 本人；不改岗位快照 |
| 前进 | POST | `/api/v1/applications/{id}/advance` | 本人；体 `{"to":"SUBMITTED"}` |
| 更正 | POST | `/api/v1/applications/{id}/correct` | 本人；必须填 `reason` |
| 接受后撤回 | POST | `/api/v1/applications/{id}/withdraw-after-accept` | 本人；须 `confirmAck=true` |
| 归档投递 | POST | `/api/v1/applications/{id}/archive` | 本人；不解除已绑定版本 |
| 读时间线 | GET | `/api/v1/applications/{id}/timeline` | 本人 |

错误类别沿用 S0。关键 reason：

- `UNCONFIRMED_AI_FACTS`：未确认 AI 事实不得可投递 / 冻结（409）
- `OUTCOME_EVIDENCE_REQUIRED`：关键成果无证据且未逐条确认（409）
- `MASTER_NOT_READY_TO_APPLY`：主档不是可投递不得冻结（409）
- `RESUME_VERSION_NOT_FROZEN`：无已冻结 / 已绑定版本不得建投递（409）
- `JOB_VERSION_NOT_CONFIRMED`：岗位未确认不得建投递（409）
- `ILLEGAL_STAGE_TRANSITION`：非法流转，说明当前态与允许去向（409）
- `OFFER_ACCEPTED_ONLY_WITHDRAW_AFTER_ACCEPT`：Offer已接受只能接受后撤回（409）
- `NEED_CONFIRM_WITHDRAW_AFTER_ACCEPT`：未二次确认（400），文案展示影响
- `CORRECTION_REASON_REQUIRED`：更正未填原因（400）
- `VERSION_CONFLICT`：乐观版本号不匹配，拒绝覆盖
- `RESUME_VERSION_IMMUTABLE`：已冻结 / 已绑定不可原地改
- `OBJECT_FORBIDDEN`：不能访问他人对象
- `OPERATOR_NO_ORIGINAL`：运营默认不能看原文

### 三套状态（未发明新名，英文码对应确认记录中文）

| 对象 | 状态 |
| --- | --- |
| 简历主档 | `DRAFT` 草稿 / `PENDING_CONFIRMATION` 待确认 / `READY_TO_APPLY` 可投递 / `ARCHIVED` 已归档。归档可恢复为归档前状态 |
| 简历版本 | `GENERATING` 生成中 / `PENDING_USER_CONFIRMATION` 待用户确认 / `FROZEN` 已冻结 / `BOUND` 已绑定投递 / `ARCHIVED` 已归档 |
| 投递主链 | `PENDING_SUBMIT` → `SUBMITTED` → `VIEWED` → `WRITTEN_TEST` → `INTERVIEWING` → `OFFER_RECEIVED` → `OFFER_ACCEPTED` / `OFFER_DECLINED` |
| 投递终止态 | `WITHDRAWN`、`REJECTED_BY_EMPLOYER`、`JOB_CLOSED`、`NO_REPLY`、`WITHDRAWN_AFTER_ACCEPT`。不设「结束」 |

P0A 模板：`SOFTWARE_DEV`、`QA`、`DATA_ANALYSIS`、`PRODUCT`。导入与 AI 先入候选。进入面试中不建轮次。

### 冻结与绑定

- 仅 `READY_TO_APPLY` 可冻出 `FROZEN`。同一事务内校验证据并 `registerActiveReference`；失败回滚，不产生半成品版本。
- 创建投递：先校验可绑定版本 + `JobService.requireConfirmedForBinding`，再把版本改为 `BOUND` 并插入投递。失败不改简历内容。
- 归档投递不改版本状态，已绑定保持 `BOUND`。
- 匹配报告不是前置。Matching 模块未增加任何建投递调用。

### 投递流转（照录 7.3.20–7.3.21）

前进边未增减。更正须填原因，按主链栈回到实际上一主链状态，不能跳步。`OFFER_ACCEPTED` 不能退回或并入主动放弃，只能 `WITHDRAWN_AFTER_ACCEPT`（二次确认 + 展示影响）。每次变化写时间线：操作者、时间、来源 `USER`、前状态、后状态。通知失败不回滚投递。

### 删除 / 导出挂接

删除编排新增 `resume`、`application` 回执。导出 JSON 含主档元数据、版本元数据、投递与时间线；`pendingModules` 不再含这两项，面试 / 复盘仍 pending。

### Worker

`ResumeWorker` 同进程轮询 `RESUME_CUSTOMIZE` / `RESUME_PDF_EXPORT`。定制启发式只改写自我介绍前缀，不虚构公司、时间、项目或成果数字。

## 2. 怎么启动

同 S0/S1/S2，见 `backend/README.md`。

```bash
cd backend
mvn test
mvn spring-boot:run
```

健康检查 `GET /api/v1/health` 的 `slice` 为 `S3`。

## 3. 验收门对照

| 门 | 结果 |
| --- | --- |
| MOCK-RESUME-002 未确认 AI 不得可投递 / 冻结 | `ResumeFreezePolicyTest`、`ResumeApplicationIT`：`UNCONFIRMED_AI_FACTS` |
| MOCK-RESUME-003 无证据须逐条确认才能冻结 | 未处理 → `OUTCOME_EVIDENCE_REQUIRED`；`waive.confirmed=true` 后可标可投递 |
| MOCK-RESUME-004 已绑定不可覆盖、不可原地改 | 绑定后版本 `BOUND` 且 `immutable=true` |
| MOCK-RESUME-005 定制成功待用户确认 | 任务成功后 `PENDING_USER_CONFIRMATION`，不是 `FROZEN` |
| MOCK-CRM-002 无冻结版本或岗位未确认 | 草稿 ID 当版本 → 400；未确认岗位 → `JOB_VERSION_NOT_CONFIRMED` |
| MOCK-CRM-003 非法流转 | `待投递` 直接 `INTERVIEWING` → `ILLEGAL_STAGE_TRANSITION` |
| MOCK-CRM-004 Offer已接受只能撤回 | 前进主动放弃 / 更正均 `OFFER_ACCEPTED_ONLY_WITHDRAW_AFTER_ACCEPT` |
| MOCK-CRM-005 并发冲突 | 旧 `expectedVersion` → `VERSION_CONFLICT` |
| MOCK-CRM-006 归档不解除绑定 | 归档后 `resumeVersionId` 仍在，版本仍 `BOUND` |
| 冻结失败无半成品 | 假证据 ID 冻结失败后版本条数不变 |
| 对象级授权 / 运营禁原文 | 与 S0–S2 同一套 `OBJECT_FORBIDDEN` / `OPERATOR_NO_ORIGINAL`。本轮 IT 覆盖他人读简历/投递 → `OBJECT_FORBIDDEN` |
| 复制主档不带投递 / 归档恢复 | `ResumeApplicationContractIT`：副本 `DRAFT`、无版本；投递仍绑原版本。归档后恢复为 `READY_TO_APPLY` |
| 导入入候选 | `IMPORT` 主档 `PENDING_CONFIRMATION`，候选 total>0；未知模板 `RESUME_TEMPLATE_UNKNOWN` |
| 记录已投递 + 备注公司名 | `POST /applications/record` → `SUBMITTED`；`companyAlias` 不改 `jobCompanySnapshot` |
| 更正须原因并写时间线 | HTTP `correct` + timeline `source=USER` |
| 待确认定制不得建投递 | `RESUME_VERSION_NOT_FROZEN`；失败后已冻结版本仍 `FROZEN` |
| 进入面试中不建轮次 | 投递 JSON 无 `rounds` / `interviewRounds` |
| 匹配不自动建投递 | `JobMatchingIT`：匹配成功后 `/applications` 为空 |
| 导出 / 删除挂接 | 导出含 `resume`/`application`；`pendingModules` 仅面试/复盘。删除回执 `resume`/`application`=`SUCCEEDED` |
| 前进边照录 7.3.20 | `ApplicationStageMachineTest.forwardEdgesMatchConfirmedRecord` |

`mvn test`：**本轮硬化后复跑全绿，随后 S5 续开**（见巡更第 4 次 tick 日志）。未开 Vue。

S3 本刀及硬化：`ResumeFreezePolicyTest`、`ApplicationStageMachineTest`、`ResumeApplicationIT`、`ResumeApplicationContractIT`。

## 4. 前端还不能接哪些

调度官未宣布「S3 后端可交接」前，前端按约定按兵不动。即便消费本契约，也 **不能** 接：

- 正式 Vue / Mock 工程、S3 页面视觉
- 面试轮次、复盘、工作台卡片（S4/S5）
- 收藏、漏斗、批量改状态、批量导入投递
- 未确认就标可投递；覆盖已绑定版本
- 匹配失败 / 无法判断自动建投递
- 进入面试中强制建轮次
- 改 Matching 权重或打分

## 5. 未做事项

- 未做正式前端 / Mock、未开 S4、未开 S0–S2 前端
- 未接真实 LLM；导入 / 定制均为启发式候选
- PDF 为最小合法 PDF 落私有文件，无完整中文字体嵌入
- 被分享人只读走数据权利，本刀未单开简历分享 API
- P0A 无主档 / 投递物理删除按钮；彻底删除走数据权利
- 未 git commit
- 未接真实 SMTP / S3（沿用 S0）

## 6. 请求体约定

鉴权同 S0：HttpOnly Cookie `jobproof_session`。乐观锁：`expectedVersion` 可选；传错 → `VERSION_CONFLICT`。

### 6.1 创建主档

```json
{"mode":"BLANK","title":"后端简历"}
{"mode":"TEMPLATE","templateCode":"SOFTWARE_DEV"}
{"mode":"IMPORT","importText":"..."}
```

### 6.2 冻结闸

`PUT` 可带 `keyOutcomes:[{id,text,evidenceId,waiveNoEvidence}]`。冻结前须：无 `PENDING` 候选；每条成果有 `evidenceId` 或已 `waive`。

### 6.3 创建投递

```json
{"resumeVersionId":"...","jobVersionId":"...","channel":"BOSS","note":"...","companyAlias":"备注公司名"}
```

`companyAlias` 可改，不改 `jobCompanySnapshot`。

### 6.4 阶段

`advance` / `correct` 的 `to` 使用英文码（见第 1 节表）。更正必须 `reason`。接受后撤回必须 `confirmAck: true`。

### 6.5 本轮补测的边界（不发明规则）

- 复制主档只出新草稿，不复制投递、不复制版本。
- 主档归档可恢复为归档前状态；已归档不可标可投递。
- 他人对象一律 `OBJECT_FORBIDDEN`。
- 未逐条 `confirmed=true` 不得 waive：`OUTCOME_WAIVE_CONFIRM_REQUIRED`。
- `GENERATING` / `PENDING_USER_CONFIRMATION` / `ARCHIVED` 版本不可建投递。
- Matching 模块无建投递调用；匹配成功不产生投递记录。
- 进入 `INTERVIEWING` 只改投递主状态，本切片响应不含轮次字段。
- 账号导出 `included` 含 `resume` / `application`；`pendingModules` 不再含这两项。

---

> 巡更第 4 次 tick（2026-08-18）：S4 交付说明仍不存在，本文件只补边界对照，不开 S4/S5，不写 Vue。
