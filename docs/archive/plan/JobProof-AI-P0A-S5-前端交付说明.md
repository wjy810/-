# JobProof AI P0A S5 前端交付说明

> 日期：2026-08-18
> 角色：S5 前端官（工作台只读汇总收口）
> 编码：UTF-8
> 技术：Vue 3 + TypeScript + Vite。禁止用 Python 跑本项目。
> 性质：本刀可运行工作台页面 + 契约消费说明 + 未做事项。**不是**卡片布局冻结，**不是**工作台写入，**不是** P0B 通知中心。未 git commit。未改 backend Java。未改投递 / 面试 / 复盘 / 简历页。

圣旨已放权且禁止询问。沿用 S0–S4「验真案牍 / Proof Folio」。S4 已把即将面试 / 待复盘 / 通知链到真实页，本刀保持这些链接，只收口工作台只读汇总。

## 1. 本切片交付了什么

工程仍在 `frontend/`。开发时 Vite 把 `/api` 与 `/internal` 代理到 Spring Boot `http://127.0.0.1:8080`。浏览器只打 `http://localhost:5173`，Cookie `jobproof_session`，`credentials: 'include'`。

**未改 backend Java。** 工作台数据唯一真相源是 `docs/plan/JobProof-AI-P0A-S5-后端交付说明.md` 的 `GET /api/v1/workspace`（别名 `/api/v1/workbench`）。无正式 Mock 业务数据。`npm run build`（`vue-tsc -b && vite build`）本机已通过。

工作台路由 `/workspace` 已存在，本刀只接线，未大改 `router.ts`，未改顶栏 S0–S4 入口。

### 已接读取块（字段名以后端为准）

| 块 | 后端键 | 有数据 | 空态 / 下一步 | 禁止在本页做的事 |
| --- | --- | --- | --- | --- |
| 求职画像完成度 | `profileCompleteness` | `percent`、`directionConfirmed`、`snapshotVersion`；打开 `/profile` | 方向未确认时提示须先确认，不把未知补成事实 | 不能在工作台改画像 |
| 能力证据完成度 | `evidenceCompleteness` | 六类覆盖 / `activeCount` / `pendingSupplementCount`；打开 `/evidences` | 0 覆盖是合法空；待补证不写成能力不足 | 覆盖占比不进匹配计分 |
| 最近导入的岗位 | `recentJob` | `present=true` 时打开 `/jobs/:jobId` | `present=false` → 粘贴 JD | 不捏造岗位名 |
| 当前单岗位匹配 | `currentMatch` | CURRENT 报告打开 `/matching/reports/:id` | 无报告不写 0 分；有 `jobVersionId` 可看当前匹配 | 「无法判断」不展示人造总分，不能自动建投递 |
| 当前简历版本 | `currentResumeVersion` | 已冻结/已绑定打开编辑器；`present=false` 但有 `masterId` 仍可进主档 | 无版本不假装可投递 | 未确认 / 生成中版本不当正式事实；本页不能冻结 |
| 当前投递进度 | `currentApplications` | 最多 20 条，打开 `/applications/:id` | 空列表合法；下一步投递看板 / 准备投递 | 不能改阶段、不做漏斗 |
| 即将面试 | `upcomingInterviews` | 打开 `/interviews/:id` 与该投递轮次列表 | 0 轮合法；从投递进入 | 不能建轮次；结果不回写投递 |
| 待复盘 | `pendingReviews` | 打开 `/reviews/:id`；有 `interviewRoundId` 才显示关联轮次 | 空列表合法；从投递进入 | 不能建复盘；未确认建议不当弱项 |
| 异步任务 | `tasks` | 按 `taskType` 打开岗位 / 简历 / 待复盘 / 通知 | 无进行中或失败任务 | 本页不取消、不重试 |
| 当前待办 | `todos` | 派生项按 `href` / `type` 打开已有页 | 无待办是合法空 | 没有独立待办表，不能在本页勾掉 |
| 最小通知（旁路） | 不是 workspace 十键；`GET /api/v1/notifications` | 最多 5 条最近记录，打开 `/notifications` | 空通知合法 | 本页不能标已读 |

单块 `ok=false`：展示后端 `reason` / `message`，**不补默认事实**，不影响其他块。整页 401 走既有会话失效。整页 403 `OPERATOR_NO_ORIGINAL` → `ForbidState`。

`href` 仍显示为「来源契约」；已映射到 S0–S4 页面的再给「打开页面」。未映射的只作回溯，不是写入按钮。

JSON **不含**漏斗、风险、推荐、收藏；页面也没有这些入口。

### 红线落地

- 工作台无 POST/PUT/PATCH/DELETE。没有从卡片改投递阶段、建轮次、建复盘、冻结简历的按钮。
- 匹配等级为 `无法判断` 时，总分文案为「服务端未输出总分」，即使后端误带数字也不当作成绩。
- `PENDING_USER_CONFIRMATION` / `GENERATING` 简历版本标为未确认 AI，不能当已冻结事实。
- S4 顶栏「面试 / 复盘」仍落到 `#upcoming-interviews` / `#pending-reviews`；「通知」仍到 `/notifications`。数据加载后再滚到锚点。

## 2. 页面清单

| 路由 | 谁能进 | 做什么 |
| --- | --- | --- |
| `/workspace` | 须登录 | 十块只读汇总 + 通知旁路只读；入口只跳 S0–S4 已有页 |

未新开工作台子路由。未登录访问 → `/login?reason=unauthenticated&next=…`。

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

浏览器打开：**http://localhost:5173**（已登录则落在工作台）。

生产构建：

```bash
cd frontend
npm run build
npm run preview
```

`preview` 没有开发代理。联调请用 `npm run dev`。健康检查 `GET /api/v1/health` 的 `slice` 应为 `S5`。

### 本刀可演示路径

1. 空账号登录 → 十块可读。待办含 `PROFILE_DIRECTION`，打开画像。匹配块无总分。无漏斗 / 收藏。
2. 方向未确认时，画像块提示不能正式匹配；工作台不能改画像。
3. 有 CURRENT 报告且等级为无法判断 → 不展示人造总分，可打开报告。
4. 有投递 / 已排期轮次 / 待复盘 → 分别打开投递详情、轮次、复盘。空轮次不出现「创建轮次」按钮。
5. 顶栏「面试」「复盘」滚到对应锚点。通知旁路只读，标已读仍在通知页。
6. 运营 403 → `ForbidState`，不展示原文汇总。

## 4. 状态覆盖

| 态 | 何处可看 |
| --- | --- |
| 正常 | 十块按后端字段展示；有数据可点进对应页 |
| 加载 | 骨架；按钮「正在读取…」；通知旁路独立骨架 |
| 空 | `present=false` / 空数组斜体说明 + 下一步指引 |
| 错误 | 整页 `message`；单块 `ok=false` 的 reason/message |
| 权限 | 403 `ForbidState`；401 会话失效回登录 |
| 禁用 | 文案写明本页不能改阶段 / 建轮次 / 建复盘 / 冻结 / 标已读 |
| 无法判断 | 匹配块不展示人造总分 |
| 未确认 | 简历待确认 / 生成中不当正式冻结事实 |

## 5. 后端缺口（只记录，未改 backend）

1. 工作台没有通知块。通知旁路另打 `GET /api/v1/notifications`，不假装它是 workspace 十键。
2. `无法判断` 时后端交付说明要求省略 `totalScore`。前端仍按等级隐藏数字，防止误带 0。
3. 没有「本人全部轮次/复盘」列表 API。顶栏面试/复盘仍接工作台只读块。
4. `REVIEW_ANALYZE` 任务没有 `reviewId`，仍链到待复盘锚点。
5. `ACCOUNT_EXPORT` / `ACCOUNT_DELETION` 没有数据权利页，任务链到通知页看进度。
6. 即将面试未做「未来 N 天」窗口（有时间的已安排/进行中即列出），与后端一致。

## 6. 未做事项

本刀**故意不做**：

- 冻结卡片排序、快捷操作方案
- 工作台写接口，或从卡片改投递阶段 / 建轮次 / 建复盘 / 冻结简历
- 漏斗、风险提醒、多岗位推荐、收藏
- 数据权利页（导出 / 删除申请）
- 完整通知中心、邮件短信、日历、偏好中心
- 重写 CRM / 简历 / 面试 / 复盘页；未改 `features/application`、`features/interview`、`features/review`、`features/resume`
- 大改 `router.ts` 与顶栏 S0–S4 路由
- P0B / P1 / P2、录音、准备包、企业付费查询、薪资谈判
- git commit
- 改 backend；用 Python 跑项目

## 7. 验收门对照

| 门 | 本刀 |
| --- | --- |
| PRD 23.1-9 汇总主链读取 | `/workspace` 消费十块；空态各块可读 |
| 架构 19.2 / 读取边界 | 十块齐全；无漏斗/风险/推荐/收藏 |
| 无工作台写接口 | 页面无写按钮；写入只跳领域页 |
| 缺失不填默认事实 | 无报告 `present=false`；无法判断不展示人造总分 |
| 待办可追溯 | `PROFILE_DIRECTION` 等到 `/profile` 等已开门页 |
| S4 面试/复盘/通知链接 | 保持；加载后锚点滚动 |
| `npm run build` | 已通过 |

## 8. 目录

```txt
frontend/src/features/workspace/pages/WorkspacePage.vue
frontend/src/features/workspace/components/WorkspaceFolio.vue
frontend/src/features/workspace/components/WorkspaceNoticeGlance.vue
frontend/src/features/workspace/components/SourcePath.vue
frontend/src/features/workspace/readout.ts
frontend/src/features/workspace/labels.ts
frontend/src/features/workspace/types.ts
frontend/src/features/workspace/services/workspaceApi.ts
frontend/src/shared/lib/workspaceNav.ts   待办 / 任务跳转收口（未删 S4 interviews/reviews 映射）
```

## 9. 回滚

还原 `features/workspace` 本刀文件、`workspaceNav.ts` 与 `style.css` 新增类，即撤回本刀收口。勿用本页字段倒逼改契约。S0–S4 领域页不受影响。
