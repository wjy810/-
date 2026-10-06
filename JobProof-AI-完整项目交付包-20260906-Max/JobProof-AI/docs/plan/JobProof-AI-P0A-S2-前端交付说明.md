# JobProof AI P0A S2 前端交付说明

> 日期：2026-08-18
> 角色：S2 前端切片官（JD 文本导入 / 确认岗位 / 单岗位匹配报告）
> 编码：UTF-8
> 技术：Vue 3 + TypeScript + Vite。禁止用 Python 跑本项目。
> 性质：本刀可运行页面 + 契约消费说明 + 未做事项。**不是**简历编辑器或投递看板。未 git commit。未改 backend Java。

圣旨已放权且禁止询问。沿用 S0/S1「验真案牍 / Proof Folio」：暖纸、蜡印、衬线标题。elite-web-designer 未走 Phase 1–6 交互问询与生图；本刀只把同一套案卷语言接到 JD / 匹配，不另起第二套页面。

## 1. 本切片交付了什么

工程仍在 `frontend/`。开发时 Vite 把 `/api` 与 `/internal` 代理到 Spring Boot `http://127.0.0.1:8080`。浏览器只打 `http://localhost:5173`，Cookie `jobproof_session`，`credentials: 'include'`。

**未改 backend Java。** 只消费 S2 已确认契约。`npm run build`（`vue-tsc -b && vite build`）本机已通过。

### 已接操作（只消费 S2 已确认契约）

| 能力 | 方法 | 路径 | 页面表现 |
| --- | --- | --- | --- |
| 粘贴导入 JD | POST | `/api/v1/jobs/import` | `/jobs` 校验规范化后 80–20000 字；过短过长可见失败，保留文本 |
| 岗位列表 / 详情 | GET | `/api/v1/jobs`、`/jobs/{id}` | 列表进确认页；版本列表可切换 |
| 岗位版本 | GET | `/api/v1/jobs/versions/{id}` | `/jobs/versions/:id` 收口到同一确认页 |
| 确认 / 校正七项 | POST | `/jobs/versions/{id}/confirm` | 空值保持未知，不显示为符合；可能重复可选择并入 |
| 归档岗位版本 | POST | `/jobs/versions/{id}/archive` | 二次确认；归档后不能匹配 |
| 取消 / 手动重试解析 | POST | `/jobs/parse-tasks/{id}/cancel\|retry` | PENDING/RUNNING 可取消；结构失败只手动重试 |
| 匹配前门 | POST | `/api/v1/profile/matching-gate` | 无方向拦截并指向 `/profile` |
| 发起单岗位匹配 | POST | `/api/v1/matching/jobs/{jobVersionId}` | 仅 `CONFIRMED` 可点 |
| 取消 / 手动重试匹配 | POST | `/matching/tasks/{id}/cancel\|retry` | 同任务五态 |
| 当前 / 列表 / 详情报告 | GET | `/matching/reports/current`、`/reports`、`/reports/{id}` | 总分、四维、加分/扣分/缺失、证据、依据、置信、未知、等级 |
| 对比两份报告 | GET | `/reports/{id}/compare?with=` | STALE 可对比 |
| 归档报告 | POST | `/matching/reports/{id}/archive` | 须主动；无物理删除 |

错误类别原样展示服务端 `message` / `reason`。401 且原先已登录 → 回登录页。403 `OPERATOR_NO_ORIGINAL` / `OBJECT_FORBIDDEN` → 封存页。

### JD / 确认 / 匹配规则（未发明）

- 解析只是候选；用户确认后才成正式岗位事实。
- 七项：岗位名称、主要职责、硬性技能、经验要求、学历要求、地点、工作方式。未知保持未知，页面打「未知」印，不得显示为符合。
- 仅 `CONFIRMED` 可发起匹配。无求职方向 `DIRECTION_NOT_CONFIRMED`，指向画像，不发起。
- 多岗位混合：提示只确认其中一个岗位；确认后 `mixedJobs=false`。
- 全文相同复用快照与岗位版本；公司名+岗位名相同但正文不同只提示 `possibleDuplicateJobId`，由用户选择并入。
- 报告必须可解释。等级只允许：高度匹配 / 部分匹配 / 缺口明显 / 无法判断。禁止只给黑盒百分比。
- `无法判断` 时不展示误导性高置信度；分项数字只供对照。
- 空限制维未参与时写明 10% 已按 30:35:25 重分。
- `STALE` 可查看并提示「输入已变化」；不自动重算；用户可手动重算。
- `PENDING` / `RUNNING` 可取消。结构/非 JD/超长超短不自动重试，只手动。
- **没有**收藏、多岗位推荐、自动建投递。投递按钮禁用，并写明须 S3。

### 工作台

顶栏与工作台「最近导入的岗位 / 当前单岗位匹配」可导航到本刀页面。`href` 仍显示来源契约；已映射的再给「打开页面」。任务 `href` `/api/v1/tasks/{id}` 落到 `/jobs?taskId=`。

## 2. 页面清单（同一套路由，不两套）

| 路由 | 谁能进 | 做什么 |
| --- | --- | --- |
| `/` | 皆可 | 已登录 → 工作台；否则 → 登录 |
| `/login` `/register` `/reset` | 仅游客 | S0 会话 |
| `/workspace` | 须登录 | 十块只读；可去画像 / 证据 / 岗位 / 报告 |
| `/profile` | 须登录 | S1 画像；前门通过后可去 `/jobs` |
| `/evidences` `/evidences/:id` | 须登录 | S1 证据 |
| `/jobs` | 须登录 | 粘贴 JD、字数校验、解析进度、岗位列表 |
| `/jobs/:jobId` | 须登录 | 校正确认七项、发起匹配、历史报告 |
| `/jobs/versions/:versionId` | 须登录 | 收口到 `/jobs/:jobId?versionId=`，不是第二套页 |
| `/matching/current` | 须登录 | 按 `jobVersionId` 取 CURRENT，再落到报告详情 |
| `/matching/reports/:id` | 须登录 | 可解释报告、STALE 提示、对比、手动重算、归档 |
| `/account` | 须登录 | 改密 |

未登录访问须登录路由 → `/login?reason=unauthenticated&next=…`。

若巡更或其他切片也写 JD 页，必须以本表路由为准，不得再开 `/jd`、`/match` 等平行页面。

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

生产构建（本机已跑通 `vue-tsc -b && vite build`）：

```bash
cd frontend
npm run build
npm run preview
```

`preview` 没有开发代理。联调请用 `npm run dev`。

### 本刀可演示路径

1. 登录 → 工作台 → 打开岗位，或画像前门通过后「去粘贴 JD」。
2. 粘贴不足 80 字 / 超过 20000 字：前端拦截，失败可见，文本仍在。
3. 粘贴非 JD（几乎无岗位信号）：解析任务 `FAILED`，失败原因可见，可改文本后重新导入或手动重试。
4. 粘贴合格 JD → 看 PENDING/RUNNING 进度 → 进入确认页。全文相同再次导入应提示复用。
5. 七项留空确认：字段打「未知」，不是符合。仅确认后「开始单岗位匹配」可点。
6. 不填求职方向去点匹配：拦截并指向 `/profile`。
7. 匹配完成后打开报告：看见等级、总分、四维、加分/扣分/缺失、证据、依据、置信、未知项。`无法判断` 不以百分比冒充等级。
8. 改画像或再确认岗位后，旧报告变 STALE：仍可打开，提示输入已变化，须手动重算。
9. 进行中任务可取消；终态取消应失败。结构失败不会自动再跑。

## 4. 状态覆盖

| 态 | 何处可看 |
| --- | --- |
| 正常 | 确认后匹配出 CURRENT 报告，等级与分项可见 |
| 加载 | 导入/确认/报告骨架；按钮「正在…」 |
| 空 | 无岗位、无当前报告的斜体说明 |
| 错误 | 后端未接通开机页；接口 `message`；`VERSION_CONFLICT` 拒绝覆盖并重读 |
| 校验 | 80–20000 字未过不提交 |
| 提交中 | 导入/确认/匹配/归档按钮禁用 |
| 403 | `ForbidState` |
| 禁用 | 未确认不能匹配；投递按钮禁用并说明须 S3 |
| STALE | 报告页横幅 + 岗位页「输入已变化」；不自动重算 |
| 任务 | PENDING/RUNNING 取消；FAILED/CANCELLED 手动重试 |

## 5. 未做事项

本刀**故意不做**：

- 简历编辑器 / 冻结 UI、投递看板、自动建投递、面试轮次、复盘
- 收藏、多岗位推荐 / 排名、漏斗、企业风险、改权重
- 自动重算 STALE、把未知补成符合、空限制白送分、非全文相同自动并岗
- 报告物理删除、真实 LLM 解析（后端启发式候选）
- 最小通知、数据权利（仍属 S0 尾巴）
- 手机号、第三方、MFA、运营后台
- P0B / P1 / P2、App / 扩展
- git commit
- 改 backend；全量 `mvn test` 不在本刀修

## 6. 验收门对照

| 门 | 本刀 |
| --- | --- |
| 确认记录 4.1 粘贴 + 确认七项 + 可解释报告 | `/jobs` → `/jobs/:id` → `/matching/reports/:id` |
| 确认记录 4.2–4.4 四维与等级 | 报告展示四维、加分上限说明、未知不打符合、无法判断不冒充高置信 |
| 确认记录 4.5 80–20000、非 JD、取消/重试、STALE | 导入校验与失败可见；任务五态按钮按契约；STALE 可看可对比可手动重算 |
| MOCK-PROFILE-002 无方向不得正式匹配 | 确认页拦截并指向画像 |
| 仅 CONFIRMED 可匹配 | 未确认按钮禁用；服务端 409 仍展示 |
| 工作台可导航至本刀 | 最近 JD / 当前匹配 / 任务 href 已映射 |
| 无收藏、无多岗推荐、无自动建投递 | 无入口；投递按钮禁用并写明须 S3 |

## 7. 目录

```txt
frontend/src/features/job/         导入、确认七项、解析任务、版本收口
frontend/src/features/matching/    报告、对比、手动重算、归档
frontend/src/shared/api/task.ts    任务五态与结构失败判断
frontend/src/shared/lib/pollTask.ts
frontend/src/shared/lib/workspaceNav.ts  映射 jobs / matching；任务仅 JD_PARSE / JOB_MATCH
frontend/src/app/router.ts         本刀路由
```

## 8. 回滚

删除 `features/job`、`features/matching`，还原 `router.ts`、顶栏、工作台链接与 `workspaceNav.ts`，即撤回本刀页面。勿用本页字段倒逼改契约。

## 9. 本轮硬化（巡更当值官，2026-08-18 第 7 次 tick）

开场时 S3 前端交付说明未落盘，本轮只硬化 S0–S2，未开简历/投递/面试页。

- 工作台任务不再把导出/复盘等一律送进 `/jobs`；仅 `JD_PARSE` / `JOB_MATCH` 打开岗位导入页。
- 导入页按任务类型取消/重试；匹配成功不再伪装成解析成功。
- 确认页跟随 `versionId` 查询；混合岗禁用匹配。
- 「无法判断」工作台与对比面板不再把总分/高置信当作成绩。
- `next` 规范化 `..`；轮询最多 180 秒后停，不自动重试。
