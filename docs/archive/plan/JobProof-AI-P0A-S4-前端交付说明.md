# JobProof AI P0A S4 前端交付说明

> 日期：2026-08-18
> 角色：S4 前端切片官（面试轮次 / 基础复盘 / 最小通知）
> 编码：UTF-8
> 技术：Vue 3 + TypeScript + Vite。禁止用 Python 跑本项目。
> 性质：本刀可运行页面 + 契约消费说明 + 未做事项。**不是** S5 工作台卡片方案，**不是**录音/准备包。未 git commit。未改 backend Java。

圣旨已放权且禁止询问。沿用 S0–S3「验真案牍 / Proof Folio」：暖纸、蜡印、衬线标题。elite-web-designer 未走 Phase 1–6 交互问询与生图；本刀只把同一套案卷语言接到轮次、复盘与站内通知。

## 1. 本切片交付了什么

工程仍在 `frontend/`。开发时 Vite 把 `/api` 与 `/internal` 代理到 Spring Boot `http://127.0.0.1:8080`。浏览器只打 `http://localhost:5173`，Cookie `jobproof_session`，`credentials: 'include'`。

**未改 backend Java。** 只消费 S4 后端交付说明中的已确认契约。无正式 Mock 业务数据。`npm run build`（`vue-tsc -b && vite build`）本机已通过。

### 已接操作（唯一 API 真相源：S4 后端交付说明）

| 能力 | 方法 | 路径 | 页面表现 |
| --- | --- | --- | --- |
| 创建轮次 | POST | `/api/v1/applications/{applicationId}/interviews` | `/applications/:applicationId/interviews`；无时间→待安排；有时间→已安排。显式按钮，不静默创建 |
| 轮次列表 | GET | `/api/v1/applications/{applicationId}/interviews` | 同上；0 轮合法空 |
| 轮次详情 | GET | `/api/v1/interviews/{id}` | `/interviews/:id` |
| 改类型/时间/结果/备注 | PUT | `/api/v1/interviews/{id}` | 带 `expectedVersion`。补时间可使待安排→已安排 |
| 前进 | POST | `/api/v1/interviews/{id}/advance` | 仅 `allowedForward` 可点 |
| 取消 | POST | `/api/v1/interviews/{id}/cancel` | 记录取消前状态 |
| 清空时间回待安排 | POST | `/api/v1/interviews/{id}/unschedule` | 必须填原因 |
| 更正 | POST | `/api/v1/interviews/{id}/correct` | 必须填原因；已完成→进行中；已取消→取消前状态 |
| 发起复盘 | POST | `/api/v1/applications/{applicationId}/reviews` | `/applications/:applicationId/reviews`；`HANDWRITE`/`PASTE`；可空关联轮次 |
| 复盘列表 / 详情 | GET | `/applications/{id}/reviews`、`/reviews/{id}` | 列表与 `/reviews/:id` |
| 发起分析 | POST | `/api/v1/reviews/{id}/analyze` | 仅待生成/失败；轮询任务五态 |
| 确认 / 拒绝建议 | POST | `/reviews/{id}/suggestions/{sid}/confirm\|reject` | 仅待确认；确认才写弱项 |
| 完成本次复盘 | POST | `/api/v1/reviews/{id}/complete` | 二次确认：未处理项不写入 |
| 归档复盘 | POST | `/api/v1/reviews/{id}/archive` | 分析中禁用 |
| 取消 / 重试分析 | POST | `/reviews/analyze-tasks/{taskId}/cancel\|retry` | 按任务五态 |
| 通知列表 | GET | `/api/v1/notifications` | `/notifications`；分页 |
| 单条 / 批量已读 | POST | `/{id}/read`、`/read-batch` | 仅 `DELIVERED` 可点；`SEND_FAILED` 禁用 |

错误类别原样展示服务端 `message` / `reason`。401 走既有会话失效。403 `OPERATOR_NO_ORIGINAL` / `OBJECT_FORBIDDEN` → `ForbidState`。`VERSION_CONFLICT` 拒绝覆盖并重读。404 / `*_NOT_FOUND` 展示对象不存在。422 `REQUIRES_HUMAN` 展示服务端文案。409 非法流转 / 复盘闸展示原因。

### 后端巡更两闸（S4 契约补丁）

- **`APPLICATION_ALREADY_ARCHIVED`**：已归档投递详情禁用前进 / 更正 / 接受后撤回，并写明原因；备注仍可改。轮次 / 复盘列表创建按钮禁用并写明原因。写失败按 409 展示服务端文案，不当 500。
- **`VERSION_CONFLICT`**：投递与轮次状态变更带 `expectedVersion`。409 提示刷新并重读，拒绝覆盖，不当系统故障、不吞掉。非法流转仍走各自 reason，不与版本冲突混为一谈。

### 面试 / 复盘 / 通知规则（未发明）

- Interview / Review **不得改写投递阶段**。页面只读展示 `applicationStatus`，没有投递前进按钮。
- 进入投递 `面试中` 只提示，并链到轮次列表；**不自动创建轮次**。用户必须点「创建这一轮」。
- 轮次非法流转按钮禁用，并写明当前态与允许去向（来自 `allowedForward` / `correctTarget`）。
- 结果、完成都不回写投递。完成只展示 `reviewPrompt`，链到复盘发起页，不静默建复盘。
- 无计划时间不声称已建提醒。
- 复盘 AI 产出为候选。未确认不能当正式弱项。拒绝不进画像。完成时未处理项不写入。已确认不可覆盖。
- 通知：`SEND_FAILED` / `PENDING` 不能标已读来假装已送达。安全与数据权利通知不可关闭（无关闭开关）。

### 从 S3 投递详情接上

- 「进入面试中」横幅改为进入轮次列表，并写明须显式创建。
- 详情顶栏增加「面试轮次」「复盘」。归档后仍可查看列表；创建按钮在列表页禁用并说明原因。
- 投递看板横幅改为「从详情进入，须显式创建」，不重写 CRM。

### 工作台 / 顶栏

- 顶栏增加「面试 / 复盘 / 通知」。面试、复盘落到工作台已有只读块锚点，不重做 S5 卡片方案。
- 工作台「即将面试 / 待复盘」只接线到 `/interviews/:id`、`/reviews/:id`。`href` 仍显示来源契约。
- `workspaceNav` 映射 `/api/v1/interviews/{id}`、`/api/v1/reviews/{id}`、通知与投递下轮次/复盘列表。

## 2. 页面清单（同一套路由，不两套）

| 路由 | 谁能进 | 做什么 |
| --- | --- | --- |
| `/applications/:applicationId/interviews` | 须登录 | 列表 + 显式创建轮次 |
| `/interviews/:id` | 须登录 | 详情、合法流转、字段保存、更正 |
| `/applications/:applicationId/reviews` | 须登录 | 列表 + 手动发起复盘 |
| `/reviews/:id` | 须登录 | 分析、逐条确认/拒绝、完成、归档 |
| `/notifications` | 须登录 | 最小站内通知列表与已读 |

未登录访问须登录路由 → `/login?reason=unauthenticated&next=…`。

## 3. 怎么启动

两台进程，先后端后前端。不要用 Python。

```bash
# 终端 1 · 后端
cd backend
mvn spring-boot:run

# 终端 2 · 前端
cd frontend
npm install
npm run dev
```

浏览器打开：**http://localhost:5173**

生产构建：

```bash
cd frontend
npm run build
npm run preview
```

`preview` 没有开发代理。联调请用 `npm run dev`。

### 本刀可演示路径

1. 登录 → 投递详情 → 前进到面试中 → 看到提示 → 进入轮次列表。列表为空合法。点「创建这一轮」才出现轮次。
2. 无时间创建 → 待安排；非法点「已完成」禁用并说明允许去向。
3. 补时间保存 → 已安排。取消、进行中、完成按允许按钮。完成后提示去复盘，不自动建报告。
4. 填结果「通过」后，页面仍展示原投递主状态。
5. 并发：改版本后用旧版本再保存 → `VERSION_CONFLICT`，拒绝覆盖并重读。
6. 从轮次「去发起复盘」进入复盘页（带 `roundId` 预填），仍须粘贴/手写并提交。
7. 发起分析 → 待确认建议。确认一条才写弱项；拒绝不进画像。完成时勾选「未处理不写入」。
8. 通知页：已送达可已读；发送失败红色展示且已读按钮禁用。

## 4. 状态覆盖

| 态 | 何处可看 |
| --- | --- |
| 正常 | 创建轮次出现在列表；合法前进；确认建议 |
| 加载 | 骨架；按钮「正在…」；分析任务轮询 |
| 空 | 0 轮、无复盘、无通知的斜体说明 |
| 错误 | 接口 `message`；分析失败保留原文；`SEND_FAILED` 不伪装已发送 |
| 校验 | 复盘无文本不提交；更正/清空时间无原因不提交 |
| 提交中 | 创建/前进/确认等按钮禁用 |
| 403 | `ForbidState` |
| 404 | 轮次/复盘不存在文案（含服务端 `*_NOT_FOUND`，HTTP 多为 400） |
| 409 | 非法流转禁用 + 服务端冲突文案；版本冲突重读 |
| 422 | 展示服务端 `REQUIRES_HUMAN` 文案 |
| 禁用 | 归档投递不能建轮次/复盘；非法流转；未确认建议不能当正式弱项；发送失败不能标已读 |
| 任务 | 分析 PENDING/RUNNING 可取消；FAILED/CANCELLED 可按契约手动重试 |

## 5. 后端缺口（只记录，未改 backend）

1. `markRead` 会把任意状态写成 `READ`，包括 `SEND_FAILED`。前端禁用该操作，避免假装已送达。
2. 没有「本人全部轮次/复盘」列表 API。顶栏面试/复盘接到工作台只读块，创建仍从投递进入。
3. 复盘分析是启发式候选，不是真实 LLM。页面按任务契约展示，不假装模型供应商。
4. `TaskView` 不含 `reviewId`。工作台异步任务里的 `REVIEW_ANALYZE` 只能链到待复盘块，不能直达某一份报告。
5. `INTERVIEW_NOT_FOUND` / `REVIEW_NOT_FOUND` 走用户可修正 400，不是 HTTP 404。前端按 reason 与 404 一并展示「对象不存在」。

## 6. 未做事项

本刀**故意不做**：

- 重做 S5 工作台卡片、排序或快捷操作（只接线）
- 录音、转写、实时 Copilot、准备包、模拟面试、训练任务
- 进入面试中强制/静默建轮次
- 轮次结果 / 复盘结论回写投递
- 未确认建议写弱项
- 邮件短信、日历、Offer 截止提醒、完整偏好中心、渠道配置后台
- 多人复盘、漏斗、收藏、自动合并 JD
- 物理删除（走数据权利）
- git commit
- 改 backend；全量 `mvn test` 不在本刀修
- 用 Python 跑项目

## 7. 验收门对照

| 门 | 本刀 |
| --- | --- |
| MOCK-INTERVIEW-001 无时间轮次 | 创建时不填时间 → 待安排；文案写明无提醒 |
| MOCK-INTERVIEW-002 已完成改结果 | 结果下拉可改；只读展示投递主状态未变 |
| MOCK-INTERVIEW-003 非法轮次流转 | 禁用并说明允许去向；服务端 409 仍展示 |
| MOCK-REVIEW-001 分析失败 | 失败横幅 + 原文保留；可按任务契约重试 |
| MOCK-REVIEW-002 逐条拒绝 | 拒绝按钮文案写明不进画像 |
| MOCK-REVIEW-003 完成时尚有未处理 | 完成前勾选确认；文案写明未处理不写入 |
| MOCK-NOTIFY-002 通知失败 | `SEND_FAILED` 红色展示，已读禁用 |
| 进入面试中不强制建轮次 | 详情提示 + 列表须显式创建 |
| 无收藏、无漏斗、无录音 | 无入口 |

## 8. 目录

```txt
frontend/src/features/interview/     轮次列表、详情、状态机按钮
frontend/src/features/review/        复盘列表、详情、建议确认
frontend/src/features/notification/  最小通知列表与已读
frontend/src/shared/lib/workspaceNav.ts  映射 interviews / reviews / notifications
frontend/src/app/router.ts           本刀路由（投递子路径写在 :id 之前）
frontend/src/shared/ui/AppChrome.vue 顶栏「面试」「复盘」「通知」
```

## 9. 回滚

删除 `features/interview`、`features/review`、`features/notification`，还原 `router.ts`、顶栏、投递详情入口、工作台块链接与 `workspaceNav.ts`，即撤回本刀页面。勿用本页字段倒逼改契约。

## 10. 留给 S5

工作台十块只读摘要 **已经存在**（S0/S5 后端 + S0 前端）。本刀只把即将面试 / 待复盘 / 待办 href 接到真实页，**没有重做卡片布局**。S5 前端若还要补：

- 仍不要锁卡片排序与快捷操作
- 不要在工作台发明写入
- 数据权利页若仍属 S0 尾巴，不在本刀范围
