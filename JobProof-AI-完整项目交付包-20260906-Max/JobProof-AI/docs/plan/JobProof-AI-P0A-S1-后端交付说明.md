# JobProof AI P0A S1 后端交付说明

> 日期：2026-08-18
> 角色：S1 后端切片官（画像使）
> 编码：UTF-8
> 性质：本刀可运行 API + 契约说明 + 验收门对照。**不是**「S1 前端可开工」宣布；调度官仍需走交接仪式。未开 S2 JD/匹配，未写 Vue / 正式 Mock，未 git commit。

## 1. 本切片交付了什么

同一 Spring Boot 单体新增包：`modules.profile`、`modules.evidence`。Flyway `V2__s1_profile_evidence.sql` 只加画像正式事实/候选、证据及其文件引用、冻结引用闸。**不建** JD、简历主档、投递表。S0 登录/会话/删除/导出未推翻。

### 已实现命令 / 查询

| 能力 | 方法 | 路径 | 权限 |
| --- | --- | --- | --- |
| 读正式画像（含完整度与版本号） | GET | `/api/v1/profile` | 本人；运营禁止看原文 |
| 写正式事实（本人填写；空值视为未知并清除该字段） | PUT | `/api/v1/profile/facts` | 本人；`expectedVersion` 可选 |
| 完整度查询 | GET | `/api/v1/profile/completeness` | 本人 |
| 匹配前门（无求职方向失败） | POST | `/api/v1/profile/matching-gate` | 本人。S2 应调用领域方法 `ProfileService.assertDirectionConfirmed(accountId)` |
| 登记 AI 候选（本刀不跑模型，不写正式事实） | POST | `/api/v1/profile/candidates` | 本人 |
| 候选列表（分页） | GET | `/api/v1/profile/candidates?status=&page=&size=` | 本人 |
| 确认候选 → 正式事实 | POST | `/api/v1/profile/candidates/{id}/confirm` | 本人 |
| 拒绝候选（不进匹配/简历） | POST | `/api/v1/profile/candidates/{id}/reject` | 本人 |
| 更正候选 → 正式事实 | POST | `/api/v1/profile/candidates/{id}/correct` | 本人 |
| 证据列表（默认 ACTIVE，分页） | GET | `/api/v1/evidences?status=ACTIVE\|ARCHIVED\|ALL` | 本人 |
| 创建六类证据 | POST | `/api/v1/evidences` | 本人 |
| 证据详情 | GET | `/api/v1/evidences/{id}` | 对象级本人 |
| 更新（已引用不可改正文/类型/核心成果；可改备注） | PUT | `/api/v1/evidences/{id}` | 对象级本人 |
| 归档 | POST | `/api/v1/evidences/{id}/archive` | 对象级本人 |
| 恢复 | POST | `/api/v1/evidences/{id}/restore` | 对象级本人 |
| 复制为新证据（改正文路径） | POST | `/api/v1/evidences/{id}/copy` | 对象级本人 |
| 引用检查（供 S3 冻结同步调用） | GET | `/api/v1/evidences/{id}/reference-check` | 对象级本人 |
| 本人文件短时上传/下载 | POST/GET | `/api/v1/files`、`/files/{id}/download-url` | **复用 S0**，证据 `fileId` 只能引用本人文件 |

错误类别沿用 S0：`USER_CORRECTABLE` / `FORBIDDEN` / `CONFLICT` / `UNAUTHENTICATED` 等。关键 reason：

- `DIRECTION_NOT_CONFIRMED`：无当前求职方向，匹配入口失败
- `FORBIDDEN_PROFILE_ATTRIBUTE`：性别/年龄/民族/婚育/政治面貌/身份证/家庭住址
- `CANDIDATE_REJECTED` / `CANDIDATE_NOT_PENDING`：拒绝的候选不得成为正式事实
- `EVIDENCE_REFERENCED`：已引用改正文或硬删被拒，提示复制新证据或只能归档
- `VERSION_CONFLICT`：乐观版本号不匹配，拒绝覆盖
- `OBJECT_FORBIDDEN`：不能改他人对象

### 画像字段（未发明）

| 类别 | 字段 key |
| --- | --- |
| 匹配前必填 | `JOB_DIRECTION` |
| 建议补、缺失保持未知 | `HIGHEST_EDUCATION`、`GRADUATION_DATE`、`MAJOR`、`TARGET_CITY`、`WORK_MODE`、`REALITY_CONSTRAINTS`（可明确「无限制」） |
| 可后补 | `SKILLS`（熟练度可选）、`YEARS_OF_EXPERIENCE`、`INTERESTS`、`SALARY_EXPECTATION`（**不进匹配计分**）、`SELF_INTRODUCTION` |
| 不采集 | 性别、年龄、民族、婚育、政治面貌、身份证号、家庭住址 |

来源：`USER_ENTERED`（本人填写）或 `USER_CONFIRMED`（确认/更正候选）。AI 只出候选。

完整度：有求职方向 30%；建议六项每填一项 +8%；技能或年限 +22%，封顶 100%。薪资/兴趣/自我介绍不计入。

### 证据类型与强度

类型：`PROJECT`、`INTERNSHIP_OR_WORK`、`PORTFOLIO`、`CERTIFICATE`、`COURSE_OR_CONTEST`、`TEXT_STATEMENT`。

强度：有核验材料（文件/链接/带来源数字）→ `STRONG`；项目或实习描述且用户确认、无独立核验件 → `MEDIUM`；仅自述 → `WEAK` 且 `pendingSupplement=true`（待补证，不写「能力不足」）。

状态：`ACTIVE` / `ARCHIVED`。已归档可恢复。有效引用存在时只能归档，不能硬删；改正文须 `copy`。

S3 冻结时请调用应用服务 `EvidenceService.registerActiveReference(accountId, evidenceId, resumeVersionId)`（本刀无简历版本，测试用 stub 引用验证闸）。解除：`releaseReference`。

### 事件（只预留，不重算匹配）

外盒发出、本刀无 Matching 消费者：

| 事件名 | 何时 |
| --- | --- |
| `ProfileCandidateCreated` | 登记候选 |
| `ProfileFactConfirmed` | 确认或更正候选 |
| `ProfileFactRejected` | 拒绝候选（不改已确认快照） |
| `ProfileSnapshotChanged` | 正式事实变化；载荷含 `snapshotVersion`、`changeType`。**将来**供 S2 把相关 CURRENT 报告标 `STALE`，不自动重算 |
| `EvidenceChanged` | 证据创建/更新/归档/恢复 |

### 删除 / 导出挂接

账号删除编排中 `profile` / `evidence` 不再 `SKIPPED`，由本模块回执清理正式事实、候选、证据与引用闸。审计索引仍受限，整体仍可为 `PARTIALLY_RESTRICTED`。删除失败回执 `FAILED`，申请保持可重试。导出 JSON 含画像正式事实与证据元数据；简历/投递/面试仍在 `pendingModules`。

## 2. 怎么启动

同 S0，见 `backend/README.md`。

```bash
cd backend
mvn test
mvn spring-boot:run
```

## 3. 验收门对照

| 门 | 结果 |
| --- | --- |
| MOCK-PROFILE-002 无求职方向不得进入正式匹配 | 领域 `ProfileMatchingGate` + `POST /profile/matching-gate`；`ProfileRulesTest`、`ProfileEvidenceIT` 已演示 |
| MOCK-EVIDENCE-001 改已引用正文拒绝并提示复制 | `EvidenceBodyGuard` + IT：409 `EVIDENCE_REFERENCED`，可改备注、可归档恢复、可 copy |
| PRD 23.1-2 基础画像 + 至少一条可追溯证据 | 可写方向等正式事实；可创建带文件/链接的证据。完整产品演示仍待前端 |
| 架构 19.2 S1 候选/正式分离；用户确认生效；有效引用阻止删除且历史可追溯 | 拒绝候选不出现在正式 facts；确认后写入；引用闸 + 引用检查接口 |
| 对象级授权 | 他人证据 403 |
| 本刀领域单测 | 无方向不可匹配；拒绝候选不成正式事实；已引用改正文被拒；归档可恢复 |

S1 领域单测：`ProfileRulesTest`、`EvidenceRulesTest`（本轮硬化后含引号空白方向视为未知）。S0+S1 全量 IT 仍以 `mvn test` 为准。

**2026-08-18 巡更第 2 次：** 工作区已有未交付的 Job/Matching 半成品，`mvn test` 会在编译期失败。本刀不改那些包。S0/S1 领域单测应单独可绿；全量绿灯等 S2 交付说明落地后再核。

## 4. 前端还不能接哪些

调度官未宣布「S1 后端可交接」前，前端按约定按兵不动。即便消费本契约，也 **不能** 接：

- JD 导入、岗位版本、匹配打分与报告视觉
- 简历主档/冻结、投递 CRM（S3 才挂真实冻结引用）
- 面试、复盘、工作台卡片
- 把未知补成默认事实、采集敏感属性、用薪资计分
- 改已引用正文的入口（只能复制新证据）

S2 匹配前必须调用 `assertDirectionConfirmed()`，不要在匹配模块另写一套默认方向。只读口见第 6 节，不要扫画像/证据库表。

## 5. 未做事项

- 未做正式前端 / Mock、未开 S0 前端、未宣布 S2 可交接（工作区或有 Job/Matching 半成品，本说明不覆盖）
- 未接真实 AI；候选登记接口供后续编排写入
- 未产生真实冻结简历引用；引用表已留，S3 再挂
- 画像变化只发 `ProfileSnapshotChanged` / `EvidenceChanged`，**不**把报告标 STALE（Matching 消费方未交接）
- 未提供证据物理删除按钮；彻底删除走账号级数据权利
- 未 git commit
- 未接真实 SMTP / S3（沿用 S0）

## 6. 请求体 / 字段约定（本轮补缺口）

鉴权同 S0：HttpOnly Cookie `jobproof_session`。运营角色访问画像/证据原文 → `OPERATOR_NO_ORIGINAL`。他人对象 → `OBJECT_FORBIDDEN`。乐观锁：`expectedVersion` 可选；不传不校验，传错 → `VERSION_CONFLICT`。

### 6.1 画像写入

`PUT /api/v1/profile/facts`

```json
{ "expectedVersion": 0, "facts": { "JOB_DIRECTION": "后端开发" } }
```

- 值为 `null`、`""`、`[]`、`{}` 视为未知，**删除该正式字段**，不生成默认事实。
- 字段 key 必须是已确认枚举；性别/年龄/民族/婚育/政治面貌/身份证/家庭住址 → `FORBIDDEN_PROFILE_ATTRIBUTE`。
- 内部 JSON **不强制 schema**（确认记录只锁语义，不锁键名）。建议形态：
  - `SKILLS`：`[{ "name": "Java", "proficiency": "熟练" }]`，熟练度可省略
  - `REALITY_CONSTRAINTS`：不接受的城市/出差/夜班/销售/加班/行业，或明确「无限制」
  - `SALARY_EXPECTATION`：底线/目标/理想及月薪或年包口径；**写入可存，不进匹配计分，不计入完整度**
  - `WORK_MODE` / `HIGHEST_EDUCATION` 等：用户填写的正式值，缺失保持未知

候选：`POST /candidates` `{ "fieldKey", "proposedValue" }`；确认/拒绝可带 `{ "expectedVersion" }`；更正 `{ "value", "expectedVersion" }`。拒绝后确认 → `CANDIDATE_NOT_PENDING`。

### 6.2 证据写入

`POST/PUT /api/v1/evidences`：`type`（六类枚举）、`title`、`body`、`coreOutcome`、`url`、`fileId`（仅本人 S0 文件）、`sourcedMetric`、`note`、`expectedVersion`。

- PUT 即使只改备注也须带 `type`（校验注解 `@NotBlank`）。已引用时改正文/类型/核心成果 → 409 `EVIDENCE_REFERENCED`，提示复制。
- 列表默认 `ACTIVE`，`status=ARCHIVED|ALL`；分页 `page`/`size`。
- 导出只含证据**元数据**（类型/标题/强度/待补证/状态/文件与链接），不含正文。

### 6.3 S2 只读领域口（不要另写默认方向）

| 方法 | 语义 |
| --- | --- |
| `ProfileService.assertDirectionConfirmed(accountId)` | 无求职方向失败 `DIRECTION_NOT_CONFIRMED` |
| `ProfileService.matchingRead(accountId)` | 只返回非未知正式事实；含 `snapshotVersion`、`directionConfirmed`。薪资字段若存在也会出现，**计分时必须忽略** |
| `ProfileService.matchingSnapshot(accountId)` | 同上，key 为 `ProfileFactKey` |
| `EvidenceService.listActiveForMatching(accountId)` | 仅 `ACTIVE` 证据（含弱/待补证，标 `pendingSupplement`，不得写成能力不足） |
| `EvidenceService.registerActiveReference` / `releaseReference` | S3 冻结/解冻引用闸 |

未知字面量（`null` / `""` / `[]` / `{}` / JSON 引号内空白）不进入 matching 只读口，也不算完整度。

## 7. 本轮硬化（巡更当值官，2026-08-18 第 2 次 tick）

- `ProfileMatchingGate.isUnknownLiteral` 覆盖 JSON 引号空白；`matchingRead` / 完整度 / `matchingSnapshot` 共用，避免把未知交给匹配。
- 补本说明第 6 节请求体与 S2 只读口。S0 交付说明删除编排句改为：画像/证据由 S1 处理器回执，不再写成永远 SKIPPED。
- **未开 S3，未改 Job/Matching 包，未写前端。** 全量 `mvn test` 若因 S2 半成品编译失败，不在本刀修复。
