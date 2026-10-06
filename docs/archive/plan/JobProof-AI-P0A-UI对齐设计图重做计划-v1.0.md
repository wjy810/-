# JobProof AI — 前端 UI 全面对齐设计图重做计划 v1.0

> 日期：2026-08-19
> 依据：`JobProof-AI-工作台界面图-v1.0/`（00–09 共 30 张界面图）、`开发文档/JobProof-AI-P3.5-业务逻辑确认记录-v0.1.md`（已冻结契约）
> 用户已确认三项决策：
> 1. 后端仅做**配合 UI 的聚合接口补/调**（如通知未读角标），不重写业务逻辑；
> 2. 范围为**全部 10 个模块约 30 个页面**一次对齐，组队并行；
> 3. 视觉**完全按设计图蓝白 SaaS 风重做**，放弃现有案卷复古风。

## 1. 现状诊断

| 项 | 状态 | 证据 |
| --- | --- | --- |
| 前端构建 | **失败** | `vue-tsc`：`JobImportPage.vue` 6 处 TS6133 未使用声明（检查被用户中断，可能有更多） |
| 前端视觉 | **与设计图两套体系** | `style.css` 为米色纸面 + 衬线 + 火漆红案卷风；设计图为浅灰蓝底 + 白卡片 + 蓝色主按钮 + 左侧深色导航 |
| 后端编译 | 通过 | `mvn -q -DskipTests compile` 静默成功 |
| 后端契约 | P0A 已收口 | `JobProof-AI-P0A-联调后缺口清单.md`（2026-08-18 复审）：blocker/gap 均为 0 |
| 通知未读角标 | **缺失** | 通知模块无 unread/count 接口，需新增 `GET /api/v1/notifications/unread-count` |
| 全局面试总览 | **路由缺失** | 设计图 07_面试总览 需要 `/interviews` 全局页，现仅有 `/applications/:id/interviews` |

## 2. 设计系统（地基，Coordinator 亲做并冻结）

- 令牌：底色 `#f4f6fa`，卡片 `#ffffff` + 边 `#e5e9f0` + 圆角 12，主蓝 `#2563eb`（hover `#1d4ed8`），成功 `#16a34a`，警告 `#d97706`，危险 `#dc2626`，AI 紫 `#7c3aed`；正文 `#1f2329` / 次要 `#646a73` / 弱 `#8f959e`；系统无衬线字体栈（PingFang SC / Microsoft YaHei）。
- 共享类契约：`page / page-head / card / stat / grid-stats / btn(--primary|--ghost|--danger|--text，兼容旧 wax|ink) / tag(--blue|green|orange|red|gray|purple) / field / input|textarea|select / banner(--ink|ok|warn|bad) / tbl / empty / bone / text-link / kicker / lede / fine / muted / btn-row`。
- 共享组件（`shared/ui/`）：重写 `AppChrome`（左侧图标导航 + 顶栏 + 未读角标 + 用户卡）、`AppButton/AppField/AppBanner`（API 不变仅换肤）；新增 `AppIcon`（内联 SVG 图标集）、`AppCard`、`AppTag`、`AppModal`、`AppDrawer`、`AppEmpty`。
- 骨架导航（按设计图 01_工作台）：工作台 `/workspace`、求职画像 `/profile`、证据库 `/evidences`、岗位匹配 `/jobs`、简历工作台 `/resumes`、投递管理 `/applications`、面试与复盘 `/interviews`（新路由，集成时接入）、通知中心 `/notifications`。
- 设计图有但 P0A 契约无全局搜索接口 → 本期顶栏**不渲染搜索框**（不做假功能），仅日历入口 + 通知铃（含未读角标）+ 用户区。

## 3. 并行分工（5 个子代理 + Coordinator）

| 轨道 | 模块（file_scope） | 设计图 |
| --- | --- | --- |
| T1 | `features/workspace/**`、`features/notification/**` | 01_工作台(2)、08_通知中心(2) |
| T2 | `features/profile/**`、`features/evidence/**` | 02_求职画像(2)、03_能力证据库(4) |
| T3 | `features/job/**`、`features/matching/**` | 04_岗位匹配(6) |
| T4 | `features/resume/**` | 05_简历工作台(5) |
| T5 | `features/application/**`、`features/interview/**`、`features/review/**` | 06_投递管理(6)、07_面试与复盘(6) |
| Coordinator | `shared/**`、`app/**`、`style.css`、`App.vue`、`features/identity/**`、`features/datarights/**`、`backend/**` | 00_规范与入口(2)、09_设置与数据权利(4) |

## 4. 协作硬约束

1. 子代理**只允许改自己 file_scope 内的文件**；`shared/`、`app/router.ts`、`style.css`、`backend/` 归 Coordinator。
2. 子代理不得改后端；发现接口缺口（字段/端点缺失）→ 照常按现有契约渲染并在汇报中列出，由 Coordinator 补后端。
3. 子代理不得新增路由；需要的全局路由在汇报中列出，集成时由 Coordinator 统一加。
4. 业务行为以 P3.5 已冻结契约为准：只改 UI 与交互呈现，**不改业务规则**（如简历冻结前置、证据仅归档、匹配无法判断等）。
5. 图标一律用 `AppIcon`（SVG），禁用 emoji 充当图标；可点元素必须 `cursor: pointer`。
6. 页面状态必须覆盖：加载 / 空 / 错误 / 权限不足 / 提交中 / 禁用。

## 5. 最终验收矩阵

> 验收基线：`JobProof-AI-工作台界面图-v1.0/` 中 00–09 共 39 张正式设计图。10 目录为未来概念稿，不进入 P0A 正式路由。

| 模块 | 设计图 | 对应路由 / 入口 | 真实数据与状态覆盖 | 最终结论 |
| --- | ---: | --- | --- | --- |
| 00 规范与入口 | 2 | `/login`、`/onboarding` | 邮箱认证、记住我、注册后画像引导、必填校验、加载/错误/提交中 | **符合**；未伪装手机号登录 |
| 01 工作台 | 2 | `/workspace`、今日待办抽屉 | 聚合块独立失败、真实待办、岗位/投递/任务/面试摘要 | **符合** |
| 02 求职画像 | 2 | `/profile`、候选确认弹窗 | 正式事实、完整度、编辑、候选确认/更正/拒绝 | **符合**；不展示服务端不存在的置信度 |
| 03 能力证据库 | 4 | `/evidences`、详情抽屉/弹窗 | 类型服务端分页、新增、详情、仅归档、引用受阻 | **符合** |
| 04 岗位匹配 | 6 | `/jobs`、`/jobs/:jobId`、`/matching/*` | JD 导入校正、版本确认、任务取消、报告、过期重算 | **符合**；无独立草稿契约，不伪造草稿保存 |
| 05 简历工作台 | 5 | `/resumes`、`/resumes/:id` | 编辑、证据关联、版本对比、冻结、错误/禁用/提交中 | **符合**；旧版本回滚未冻结，明确不可用 |
| 06 投递管理 | 6 | `/applications`、详情/新增 | 看板/列表/日历同源、新增、详情、阶段状态机与更正 | **符合** |
| 07 面试与复盘 | 6 | `/interviews`、详情、`/reviews/:id` | 账号级分页、轮次、文字导入、分析、归档、弱项确认/更正/拒绝 | **符合** |
| 08 通知中心 | 2 | `/notifications` | 服务端跨页筛选、批量已读、真实异步任务状态/时间线/失败/下载 | **符合** |
| 09 设置与数据权利 | 4 | `/account`、`/account/data-rights` | 会话管理、退出其他设备、授权读取/撤销、导出/下载、删除预览/提交 | **符合** |

### 状态与契约边界

- 各轨道均覆盖正常、加载、空、错误、禁用与提交中；列表类页面同时覆盖筛选与分页，危险操作保留确认和服务端约束。
- 图片内没有冻结契约或真实后端能力的搜索、分享、录音、批量操作入口未伪装为可用。
- 10 目录的多岗位推荐、企业风险、Offer 审核、实时面试 Copilot、官方平台授权同步未进入 P0A 路由或导航。
- 首次注册会自动进入 `/onboarding`；求职方向、目标城市、工作方式完成后进入 `/workspace`。「跳过非必填项」只跳过毕业时间和专业，不能绕过必填项。

## 6. 最终验证证据

- 前端：`npm run build` 通过（`vue-tsc -b` + Vite，236 modules transformed）。
- 后端：`mvn -q -DskipTests compile` 通过。
- 后端全量：`mvn -q test` 通过；Surefire 共 **172 tests，0 failures，0 errors**。
- 岗位匹配异步测试 `JobMatchingIT` 与 `JobMatchingContractIT` 已连续定向复跑通过，原超时未复现。
- 浏览器运行态：桌面和 390px 窄屏注册页可访问、布局无横向溢出；未登录访问 `/onboarding` 会按鉴权边界跳转 `/login?next=/onboarding`；登录/注册页面标题和旧「案卷封存」文案已清理。
- 未登录启动时 `/api/v1/me` 返回 401 属会话探测的预期鉴权响应，页面正常落在登录态，不影响渲染与操作。

## 7. 回滚与遗留说明

- 当前目录未初始化 Git，无法提供提交级回滚；本轮修改按 feature 原子落地，建议在下一阶段前建立仓库和基线标签。
- 本轮没有进入 `10_未来阶段概念稿`，也没有补充未经冻结的核心业务规则。
- 当前验收结论适用于 P0A 设计图与本机开发档；生产环境仍需使用正式数据库、Redis、邮件与部署配置复验。

## 8. 历史验证与回滚约束

- 地基完成后：`npm run build`（vue-tsc + vite）必须通过；后端 `mvn -q -DskipTests compile` 必须通过。
- 每个子代理自验 `npx vue-tsc --noEmit` 无自身文件错误后汇报。
- 集成后 Coordinator 跑一次全量 `npm run build` + 后端测试冒烟。
- 工作区无 Git（未初始化）：建议用户 `git init` 建立回滚点；本次不重排目录，删除仅限被替换的旧样式类。
