# JobProof AI P0A S4 后端交付说明

> 日期：2026-08-18
> 角色：S4 后端切片官（面试复盘使）
> 编码：UTF-8
> 性质：本刀可运行 API + 契约说明 + 验收门对照。**不是**「S4 前端可开工」宣布；调度官仍需走交接仪式。未开 S5 工作台卡片，未写 Vue / 正式 Mock，未 git commit。未改 S3 投递状态机。

## 1. 本切片交付了什么

同一 Spring Boot 单体新增包：`modules.interview`、`modules.review`。Flyway `V5__s4_interview_review.sql` 只加面试轮次、复盘报告、复盘建议。通知复用 S0 `NotificationService`（补 `upsertActive` / `expire`）。弱项写入复用 S1 画像字段 `CONFIRMED_IMPROVEMENTS`（不计入完成度、不改技能等级、不进匹配计分）。

无独立「面试流程」状态机。一轮次必须属于一条投递；进入投递 `面试中` 不强制建轮次。轮次结果、复盘结论、通知送达都不得回写投递主状态。P0A 无录音、无转写、无准备包、无模拟面试。

### 已实现命令 / 查询

| 能力 | 方法 | 路径 | 权限 |
| --- | --- | --- | --- |
| 创建轮次 | POST | `/api/v1/applications/{applicationId}/interviews` | 本人；运营禁止看原文 |
| 轮次列表 | GET | `/api/v1/applications/{applicationId}/interviews` | 本人 |
| 轮次详情 | GET | `/api/v1/interviews/{id}` | 对象级本人 |
| 改类型/时间/结果/备注 | PUT | `/api/v1/interviews/{id}` | 本人；`expectedVersion` 可选。补时间可使待安排→已安排 |
| 前进 | POST | `/api/v1/interviews/{id}/advance` | 本人；体 `{"to":"IN_PROGRESS"}` |
| 取消 | POST | `/api/v1/interviews/{id}/cancel` | 本人；记录取消前状态 |
| 清空时间回待安排 | POST | `/api/v1/interviews/{id}/unschedule` | 本人；必须填 `reason` |
| 更正 | POST | `/api/v1/interviews/{id}/correct` | 本人；必须填 `reason`。已完成→进行中；已取消→取消前状态 |
| 发起复盘 | POST | `/api/v1/applications/{applicationId}/reviews` | 本人；可空关联轮次。体 `inputSource=HANDWRITE\|PASTE` |
| 复盘列表 / 详情 | GET | `/api/v1/applications/{applicationId}/reviews`、`/api/v1/reviews/{id}` | 本人 |
| 发起分析 | POST | `/api/v1/reviews/{id}/analyze` | 本人；任务五态同第二轮 |
| 确认 / 拒绝建议 | POST | `/api/v1/reviews/{id}/suggestions/{sid}/confirm`、`/reject` | 本人；仅确认写入弱项 |
| 完成本次复盘 | POST | `/api/v1/reviews/{id}/complete` | 本人；未处理建议不写入 |
| 归档复盘 | POST | `/api/v1/reviews/{id}/archive` | 本人；分析中不可归档 |
| 取消 / 重试分析 | POST | `/api/v1/reviews/analyze-tasks/{taskId}/cancel`、`/retry` | 按任务五态 |

错误类别沿用 S0。关键 reason：

- `ILLEGAL_ROUND_TRANSITION`：非法轮次流转，说明当前态与允许去向（409）
- `CORRECTION_REASON_REQUIRED`：更正 / 清空时间未填原因（400）
- `TIMEZONE_INVALID`：时区无法识别（400）
- `REVIEW_INPUT_REQUIRED`：复盘无手写或粘贴文本（400）
- `REVIEW_ROUND_MISMATCH`：关联的轮次不属于该投递（400）
- `REVIEW_ANALYZE_NOT_ALLOWED`：当前复盘态不可发起分析（409）
- `REVIEW_NOT_PENDING_CONFIRMATION`：非待确认不可逐条处理或完成（409）
- `REVIEW_SUGGESTION_NOT_PENDING`：建议已处理（409）
- `REVIEW_CONFIRMED_IMMUTABLE`：已确认复盘不可覆盖（409）
- `REVIEW_STILL_ANALYZING`：分析中不可归档（409）
- `VERSION_CONFLICT`：乐观版本号不匹配
- `OBJECT_FORBIDDEN`：不能访问他人对象
- `OPERATOR_NO_ORIGINAL`：运营默认不能看原文
- `APPLICATION_ALREADY_ARCHIVED`：投递已归档，轮次随投递归档

### 状态（未发明新名，英文码对应确认记录中文）

| 对象 | 状态 |
| --- | --- |
| 轮次类型 | `HR_SCREEN` HR初筛 / `FIRST` 一面 / `SECOND` 二面 / `FINAL` 终面 / `ADDITIONAL` 加面 / `OTHER` 其他 |
| 轮次状态 | `PENDING_SCHEDULE` → `SCHEDULED` → `IN_PROGRESS` → `COMPLETED`，另有 `CANCELLED` |
| 轮次结果 | `PENDING` 待定 / `PASSED` 通过 / `FAILED` 未通过 / `NEXT_TIME` 待下次 |
| 复盘报告 | `PENDING_GENERATE` → `ANALYZING` → `PENDING_CONFIRMATION` → `CONFIRMED`，另有 `FAILED`、`ARCHIVED` |
| 建议 | `PENDING_CONFIRMATION` / `CONFIRMED` / `REJECTED` |
| 分析任务 | `PENDING` / `RUNNING` / `SUCCEEDED` / `FAILED` / `CANCELLED`（同第二轮） |

### 轮次规则（照录 11.1）

- 创建时无时间 → `PENDING_SCHEDULE`，有时间 → `SCHEDULED`。时区默认 `Asia/Shanghai`。
- `PENDING_SCHEDULE` → `SCHEDULED`（须有时间）、`CANCELLED`
- `SCHEDULED` → `IN_PROGRESS`、`CANCELLED`；清空时间并填原因可回 `PENDING_SCHEDULE`
- `IN_PROGRESS` → `COMPLETED`、`CANCELLED`
- 更正须填原因：`COMPLETED` 可回 `IN_PROGRESS`；`CANCELLED` 可回取消前状态。不能跳步。
- 结果不改投递状态。轮次完成不自动改进投递，也不自动建复盘，只给 `reviewPrompt`。
- 无计划时间不建确定提醒。进入 `SCHEDULED` 且有时间才写 `INTERVIEW_REMINDER`。时间变更更新同一事件 ID；取消或清空时间作废旧提醒。通知失败不回滚轮次。

### 复盘规则（照录 11.2）

- 必须属一条投递，可空关联轮次。用户手动发起。只读引用该投递绑定的简历版本与岗位版本。
- AI 只出候选（问题清单、回答摘录、改进建议），不得虚构经历。
- 只有用户确认的建议写入画像 `CONFIRMED_IMPROVEMENTS`。拒绝不进画像/匹配/简历。完成时尚有未处理项不写入。
- 分析失败：报告 `FAILED`，保留原文，不写弱项；可按第二轮取消/重试。
- 已确认报告不可覆盖改写。复盘不改投递、轮次或正式技能等级。

### 通知补齐（照录 11.3）

P0A 仍只用站内。类型沿用 S0：任务完成/失败、面试提醒、导出/删除进度、安全与数据权利。去重：账号 + 事件 ID + 类型。失败不回滚业务。

### 删除 / 导出挂接

删除编排新增 `interview`、`review` 回执。导出 JSON 含面试轮次与已确认复盘；`pendingModules` 不再含这两项。

### Worker

`ReviewWorker` 同进程轮询 `REVIEW_ANALYZE`。启发式只摘录用户原文，不虚构公司、时间或成果数字。

## 2. 怎么启动

同 S0–S3，见 `backend/README.md`。

```bash
cd backend
mvn test
mvn spring-boot:run
```

健康检查 `GET /api/v1/health` 的 `slice` 为 `S4`。

## 3. 验收门对照

| 门 | 结果 |
| --- | --- |
| MOCK-INTERVIEW-001 无时间轮次 | `InterviewReviewIT`：`PENDING_SCHEDULE`，无 `INTERVIEW_REMINDER` |
| MOCK-INTERVIEW-002 已完成改结果 | 填 `PASSED` 后投递仍为创建时状态 |
| MOCK-INTERVIEW-003 非法轮次流转 | 待安排直接 `COMPLETED` → `ILLEGAL_ROUND_TRANSITION`；领域单测覆盖跳步 |
| MOCK-REVIEW-001 分析失败 | `failNext` 后报告 `FAILED`，画像无弱项；可重试 |
| MOCK-REVIEW-002 逐条拒绝 | 拒绝项不进 `CONFIRMED_IMPROVEMENTS` |
| MOCK-REVIEW-003 完成时尚有未处理 | 未处理建议 ID 不出现在弱项 |
| MOCK-NOTIFY-001 同一事件重复 | 改时间后仍只有一条面试提醒 |
| MOCK-NOTIFY-002 通知失败 | `failNextWrite` 后轮次仍为 `SCHEDULED`，通知 `SEND_FAILED` |

`mvn test`：**100 tests, 0 failures**（S0–S3 回归 + S4 领域 / IT）。2026-08-18 跑通。

S4 本刀新增：`InterviewRoundMachineTest` 5、`ReviewPolicyTest` 5、`InterviewReviewIT` 3。

## 4. 前端还不能接哪些

调度官未宣布「S4 后端可交接」前，前端按约定按兵不动。即便消费本契约，也 **不能** 接：

- 正式 Vue / Mock 工程、S4 页面视觉
- S5 工作台卡片方案
- 录音、转写、实时 Copilot、准备包、模拟面试
- 进入面试中强制建轮次
- 轮次结果 / 复盘结论回写投递
- 未确认建议写弱项
- 邮件短信、Offer 截止提醒、完整偏好中心
- 改 Matching 权重或打分

## 5. 未做事项

- 未做正式前端 / Mock、未开 S5、未开 S0–S3 前端
- 未接真实 LLM；复盘分析为启发式候选
- P0A 无录音 / 转写 / 准备包 / 训练任务
- 常规归档随投递归档，轮次无独立归档按钮
- 未 git commit
- 未接真实 SMTP / S3（沿用 S0）

## 6. 请求体约定

鉴权同 S0：HttpOnly Cookie `jobproof_session`。乐观锁：`expectedVersion` 可选；传错 → `VERSION_CONFLICT`。

### 6.1 创建轮次

```json
{"type":"FIRST","note":"先记一笔"}
{"type":"HR_SCREEN","scheduledAt":"2026-08-20T02:00:00Z","timezone":"Asia/Shanghai"}
{"type":"OTHER","customName":"业务负责人聊"}
```

### 6.2 前进 / 更正

`advance` / `correct` 的 `to` 使用英文码（见第 1 节表）。更正与清空时间必须 `reason`。

### 6.3 发起复盘

```json
{"inputSource":"PASTE","inputText":"面试问了系统设计。我答了缓存。"}
{"interviewRoundId":"...","inputSource":"HANDWRITE","inputText":"..."}
```
