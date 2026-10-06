# JobProof AI P0A S2 后端交付说明

> 日期：2026-08-18
> 角色：巡更当值官（收口 Job + Matching 后端最小闭环）
> 编码：UTF-8
> 性质：本刀可运行 API + 契约说明 + 验收门对照。**不是**「S2 前端可开工」宣布；调度官仍需走交接仪式。未开 S3 简历/投递，未写 Vue / 正式 Mock，未 git commit。

## 1. 本切片交付了什么

同一 Spring Boot 单体新增包：`modules.job`、`modules.matching`。Flyway `V3__s2_job_matching.sql` 只加 JD 原文快照、岗位、岗位版本、预置规则快照、匹配报告。解析/匹配任务复用 S0 `async_tasks`。**不建**收藏、风险、简历、投递表。S0 登录/会话/删除/导出与 S1 画像/证据未推翻。

解析：无真实模型。`JdHeuristicParser` 只出候选；用户确认后才成正式岗位事实。计分：`MatchScoringEngine` 按已发布规则算分，模型不得先出总分。预置规则 `P0A-RULE-1`，权重 30 / 35 / 25 / 10。

### 已实现命令 / 查询

| 能力 | 方法 | 路径 | 权限 |
| --- | --- | --- | --- |
| 粘贴导入 JD | POST | `/api/v1/jobs/import` | 本人；运营禁止看原文。体：`{"text":"..."}` |
| 岗位列表 | GET | `/api/v1/jobs` | 本人 |
| 岗位详情（含版本列表） | GET | `/api/v1/jobs/{jobId}` | 对象级本人 |
| 岗位版本详情 | GET | `/api/v1/jobs/versions/{versionId}` | 对象级本人 |
| 确认 / 校正岗位版本 | POST | `/api/v1/jobs/versions/{versionId}/confirm` | 本人；`expectedVersion` 可选。确认后 `mixedJobs=false` |
| 归档岗位版本 | POST | `/api/v1/jobs/versions/{versionId}/archive` | 本人 |
| 取消 / 手动重试解析 | POST | `/api/v1/jobs/parse-tasks/{taskId}/cancel`、`/retry` | 按任务五态 |
| 对已确认版本发起匹配 | POST | `/api/v1/matching/jobs/{jobVersionId}` | 本人。必须已确认岗位版本，且 `ProfileService.assertDirectionConfirmed` |
| 取消 / 手动重试匹配 | POST | `/api/v1/matching/tasks/{taskId}/cancel`、`/retry` | 按任务五态 |
| 当前报告 | GET | `/api/v1/matching/reports/current?jobVersionId=` | 本人 |
| 报告列表 | GET | `/api/v1/matching/reports?jobVersionId=` | 本人 |
| 报告详情 | GET | `/api/v1/matching/reports/{id}` | 对象级本人 |
| 对比两份报告 | GET | `/api/v1/matching/reports/{id}/compare?with=` | 本人 |
| 归档报告 | POST | `/api/v1/matching/reports/{id}/archive` | 本人 |

错误类别沿用 S0。关键 reason：

- `JD_TEXT_TOO_SHORT` / `JD_TEXT_TOO_LONG`：不足 80 字或超过 20000 字；保留原文，解析任务 `FAILED`，不创建成功解析结果
- `JOB_VERSION_NOT_CONFIRMED`：未确认岗位版本不得匹配（409）
- `DIRECTION_NOT_CONFIRMED`：无当前求职方向不得正式匹配（400）
- `MIXED_JOBS_UNRESOLVED`：多岗位混合未拆成单岗确认前不得匹配
- `TASK_NOT_CANCELLABLE`：终态不可再取消
- `OBJECT_FORBIDDEN`：不能访问他人岗位/报告
- `VERSION_CONFLICT`：乐观版本号不匹配

### 三套状态（未发明新名）

| 对象 | 状态 |
| --- | --- |
| 解析 / 匹配任务 | `PENDING` / `RUNNING` / `SUCCEEDED` / `FAILED` / `CANCELLED` |
| 岗位版本 | `DRAFT` / `REVIEW_REQUIRED` / `CONFIRMED` / `ARCHIVED` |
| 匹配报告 | `CURRENT` / `STALE` / `ARCHIVED` |

输入变化（画像正式事实、证据、岗位确认）：只把相关 `CURRENT` → `STALE`，**不自动重算**；已成功历史任务保持 `SUCCEEDED`。用户再发起匹配才出新任务和新 `CURRENT`。`STALE` 默认可查看、可对比，不自动归档。

### 打分（确认记录第 4 节，一条不改）

- 证据门槛 + 规则分；AI 只对照证据
- 四维：硬性门槛 30、技能与证据 35、经历相关 25、限制符合 10
- 空限制不白送分：限制维不参与，10% 按 30:35:25 重分
- 未知不打 0、不算缺口；1 个硬性缺口最高「部分匹配」，≥2 最高「缺口明显」
- 等级：`高度匹配` / `部分匹配` / `缺口明显` / `无法判断`
- 加分最多 +5，不能突破限等，不能把「无法判断」改成其他等级
- 匹配失败 / 取消 / 无法判断 **不**自动建投递（本刀无投递表）

报告解释 JSON 含总分、分项、加分、扣分/缺失、未知项、证据对照、规则依据、置信度。`unableToJudge` 时不给误导性高置信度。

### JD 复用

规范化（空白压缩 + 明显样板清洗）后 **全文相同** 才复用原文快照和岗位版本，不新建岗位。公司名+岗位名相同但正文不同只提示可能重复（`possibleDuplicateJobId`），由确认时 `mergeIntoJobId` 选择并入；除全文相同外不得自动合并。

### 删除 / 导出挂接

账号删除编排中 `job` / `matching` 由本模块回执清理岗位版本、原文快照与匹配报告。导出 JSON 含岗位元数据；匹配报告导出贡献仍薄。S3 起 `resume`/`application` 不再出现在 `pendingModules`。

### Worker

`JobMatchingWorker` 同进程轮询 `JD_PARSE` / `JOB_MATCH`。画像/证据/岗位确认事件由 `MatchingStaleHandler` 经 outbox 把 CURRENT 标 STALE。

## 2. 怎么启动

同 S0/S1，见 `backend/README.md`。

```bash
cd backend
mvn test
mvn spring-boot:run
```

健康检查 `GET /api/v1/health` 的 `slice` 为 `S2`。

## 3. 验收门对照

| 门 | 结果 |
| --- | --- |
| 四维与限等（确认记录 4.2–4.4） | `MatchScoringEngineTest`：权重 30/35/25/10、空限制 10% 按 30:35:25 重分、1 缺口封顶、2 缺口封顶、硬性技能整体计 1、未知不算缺口、无方向为「无法判断」、加分 +5 不能抬过限等、分数映射 60–79 / 低于 60、置信度中/低、普通用户不能改权重 |
| 三套状态 | 任务五态 / 岗位版本四态 / 报告 CURRENT→STALE→ARCHIVED；历史 SUCCEEDED 不改写。`JobMatchingIT`、`JobMatchingContractIT` |
| MOCK-PROFILE-002 无方向不得正式匹配 | `POST /matching/jobs/{jobVersionId}` → 400 `DIRECTION_NOT_CONFIRMED` |
| JD 80–20000 字 | 过短过长 `FAILED` + 保留原文。`JdTextPolicyTest`、`JobMatchingIT` |
| 全文相同才复用 | 二次导入 `reused=true`。公司名+岗位名相同但正文不同只提示 `possibleDuplicateJobId`，不自动合并；同岗名不同公司视为不同岗位 |
| 可解释报告 / 架构 19.2 S2 | 报告含分项解释；确认后才匹配；无多岗位排名；STALE 可对比，归档须用户主动 |
| 对象级授权 | 他人岗位 / 岗位版本 / 匹配报告 403。`JobMatchingContractIT` |
| 取消 / 重试 | `JobTaskCancelPolicyTest`、`TaskRetryPolicyTest`：PENDING/RUNNING 可取消；终态不可；短暂失败最多自动 2 次 |
| PRD 23.1-3/4 完整产品演示 | 后端可跑；仍待前端 |

全量 `mvn test`（2026-08-18 巡更第 3 次 tick）：**77 tests, 0 failures**。其中含工作区已出现的 S3 领域单测；**S3 交付说明仍未落地**，本刀未改 Resume/Application，未开 S4。

## 4. 前端还不能接哪些

调度官未宣布「S2 后端可交接」前，前端按约定按兵不动。即便消费本契约，也 **不能** 接：

- 正式 Vue / Mock 工程、S2 页面视觉
- 简历主档/冻结、投递 CRM（S3）
- 面试、复盘、工作台卡片
- 收藏、企业风险、多岗位排序
- 改权重、模型先出总分、自动重算 STALE、自动建投递
- 把未知补成默认事实；空限制白送分；非全文相同自动并岗

匹配入口不要另写一套默认求职方向；必须走 `assertDirectionConfirmed()`。

## 5. 未做事项

- 未做正式前端 / Mock、未写 S3 交付说明、未开 S0/S1/S2 前端、未开 S4
- 未接真实 LLM（`jobproof.ai.llm-parse-enabled` 默认关）；启发式只出候选
- 导入请求体里的 `mergeIntoJobId` 目前不在 import 路径生效，并入走确认命令
- 未提供报告物理删除按钮；彻底删除走账号级数据权利
- 未 git commit
- 未接真实 SMTP / 对象存储（沿用 S0）

## 6. 本轮硬化（巡更当值官，2026-08-18 第 3 次 tick）

- 补确认记录 4.2–4.5 规则单测：硬性技能整体计 1、加分不能抬过硬缺口限等、空限制 10% 重分到 30:35:25、分数映射、置信度低、权重冻结、短暂失败最多自动重试 2 次。
- 补 IT：他人岗位/报告 403、可能重复不自动合并、同岗名不同公司、混合 JD 不拆、STALE 可对比且归档须主动、导出含岗位元数据。
- 删除编排回执：`job` / `matching`（及 S1 画像/证据）为 `SUCCEEDED`，审计仍 `RESTRICTED` 故整体仍部分受限。
- 混合 JD：解析标 `mixedJobs=true` 且不自动拆岗；用户确认单岗后 `mixedJobs=false`，随后可匹配。
- 未碰 Resume / Application 包，未开 S4。
