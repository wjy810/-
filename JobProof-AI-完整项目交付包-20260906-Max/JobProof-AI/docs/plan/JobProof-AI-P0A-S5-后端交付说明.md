# JobProof AI P0A S5 后端交付说明

> 日期：2026-08-18
> 角色：巡更当值官（第 4 次 tick，S4 已落地后开 S5）
> 编码：UTF-8
> 性质：本刀可运行 **只读汇总 API** + 契约说明 + 验收门对照。**不是**「S5 前端可开工」宣布，**不是**卡片 UI。调度官仍需走交接仪式。未写 Vue / 正式 Mock，未 git commit。未冻结卡片、排序、快捷操作。

## 1. 本切片交付了什么

不新建写入模块、不加业务表。新增包 `modules.workspace`，聚合 S0–S4 已有只读查询。工作台不拥有业务状态；写入必须打回对应领域接口。

### 已实现查询

| 能力 | 方法 | 路径 | 权限 |
| --- | --- | --- | --- |
| 求职工作台只读汇总 | GET | `/api/v1/workspace`（别名 `/api/v1/workbench`） | 本人；运营禁止看原文 |

无 POST/PUT/PATCH/DELETE。不提供工作台专用写接口。误用写方法 → `METHOD_NOT_ALLOWED`（405）。

### 读取块（数据边界，不是卡片方案）

每块独立：`{ok, data, reason, message}`。该块失败时 `ok=false` 且 `data=null`，**不用默认事实填空**，不影响其他块。

| 块 | 来源 | 空态 |
| --- | --- | --- |
| `profileCompleteness` | `ProfileService.completeness` | percent=0，directionConfirmed=false |
| `evidenceCompleteness` | ACTIVE 证据六类覆盖占比（covered/6），另给 activeCount / pendingSupplementCount | 0 |
| `recentJob` | 最近更新岗位 + 优先 CONFIRMED 版本 | present=false |
| `currentMatch` | 对该岗位版本的 CURRENT 报告 | 无报告则 present=false；`无法判断` 或分数未知时省略 `totalScore`，不写成 0 |
| `currentResumeVersion` | 非归档主档中优先 BOUND/FROZEN 版本 | present=false |
| `currentApplications` | 未归档投递，最多 20 | [] |
| `upcomingInterviews` | 已安排/进行中且有时间的轮次 | [] |
| `pendingReviews` | 待生成 / 分析中 / 待确认 / 失败 | [] |
| `tasks` | PENDING / RUNNING / FAILED 异步任务 | [] |
| `todos` | 由上述未完成项派生，无独立待办表/状态机 | 可含确认方向、未确认候选、待确认定制、待复盘、已安排轮次、失败任务 |

`href` 只指向已有领域 API，便于回溯已确认操作。JSON **不含**漏斗、风险、推荐、收藏。

### 证据完成度说明

确认记录未单列证据百分公式。本刀用 **六类中已有 ACTIVE 证据的类型数 / 6**，只作读取覆盖，不是新计分规则，不进匹配。

## 2. 怎么启动

同 S0–S4，见 `backend/README.md`。

```bash
cd backend
mvn test
mvn spring-boot:run
```

健康检查 `GET /api/v1/health` 的 `slice` 为 `S5`。

## 3. 验收门对照

| 门 | 结果 |
| --- | --- |
| PRD 23.1-9 汇总主链读取 | `WorkspaceSummaryIT`：空态各块可读；有投递后能看到当前简历版本与投递 |
| 架构 19.2 / 读取边界 | 十块齐全；无漏斗/风险/推荐/收藏字段 |
| 无工作台写接口 | 未登录 POST 401；登录后 POST/PUT 405 |
| 对象隔离 | 他人工作台看不到本人投递/简历版本 |
| 缺失不填默认事实 | 无匹配报告时 `currentMatch.present=false`，不出现假分数 |
| 待办可追溯 | 无求职方向时 todos 含 `PROFILE_DIRECTION` → `/api/v1/profile` |

`mvn test`：**102 tests, 0 failures**（含 `WorkspaceSummaryIT` 2）。2026-08-18 巡更第 5 次再核：无方向时 `todos` 含 `PROFILE_DIRECTION`；`无法判断` 不出现 `totalScore`；登录后 PUT 工作台 405。

## 4. 前端还不能接哪些

调度官未宣布「S5 后端可交接」前，前端按兵不动。即便消费本契约，也 **不能**：

- 正式 Vue / Mock、工作台卡片布局、排序、快捷操作
- 工作台写接口、漏斗、风险提醒、多岗位推荐、收藏
- 企业付费查询、薪资谈判、合同审核、实时语音、自动发送
- 改 Matching 权重；改 S4 轮次/复盘状态机

## 5. 未做事项

- 未做正式前端 / Mock、未 git commit
- 未锁卡片 JSON
- 即将面试未做「未来 N 天」窗口（有时间的已安排/进行中即列出）
- 未接真实 LLM / SMTP / S3

## 6. 响应形状

```json
{
  "ok": true,
  "data": {
    "profileCompleteness": {"ok": true, "data": {"percent": 0, "directionConfirmed": false, "href": "/api/v1/profile/completeness"}},
    "evidenceCompleteness": {"ok": true, "data": {"percent": 0, "coveredTypeCount": 0, "typeTotal": 6, "href": "/api/v1/evidences"}},
    "recentJob": {"ok": true, "data": {"present": false}},
    "currentMatch": {"ok": true, "data": {"present": false}},
    "currentResumeVersion": {"ok": true, "data": {"present": false}},
    "currentApplications": {"ok": true, "data": []},
    "upcomingInterviews": {"ok": true, "data": []},
    "pendingReviews": {"ok": true, "data": []},
    "tasks": {"ok": true, "data": []},
    "todos": {"ok": true, "data": [{"type":"PROFILE_DIRECTION","objectId":"<accountId>","label":"确认当前求职方向","href":"/api/v1/profile"}]}
  }
}
```

空账号示例：无报告时 `currentMatch.present=false` 且无 `totalScore`。有 CURRENT 报告但等级为 `无法判断` 时同样省略 `totalScore`。`GET /api/v1/workbench` 与 `/api/v1/workspace` 同响应。

## 7. 本轮硬化（巡更当值官，2026-08-18 第 5 次 tick）

- 无求职方向派生 `PROFILE_DIRECTION` 待办，href `/api/v1/profile`。
- `无法判断` 不输出 `totalScore`；未绑轮次的复盘不输出 `interviewRoundId`（全局 `non_null`）。
- **未开 S0/S1 前端，未改 Vue，未 git commit，未开 P0B。**
