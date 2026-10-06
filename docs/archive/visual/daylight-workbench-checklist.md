# 日光下的证据工作室 · 工作台样式清单

日期：2026-09-05；2026-09-06 已续验。仅本地源码精修，不部署、不改变线上业务数据。此清单保留工作台源文件映射；最新修复、截图、PDF 与剩余边界见 [续作验收](2026-09-06-completion-report.md)。

## 设计约束

- 主动作统一使用现有 `--primary: #2563eb`；工作区浅中性灰、白色内容、少量暖白辅助区。成功、警告、错误继续使用独立语义色。
- 工作台标题 26–28px，常规阅读 14px，重要辅助文字至少 12px。只修改明确的组件规则，不使用通配字号覆盖。
- 控件约 8px、主面板约 12px；移动端补齐主要控件 44px 和底部安全区。
- 导航和列表悬停不再移位或大幅加重阴影。保留真实加载反馈、现有减少动态规则、既有图标/日期/下拉组件。
- 简历纸张维持独立排版；未改 `ResumeTemplatePreview.vue`、分页逻辑、A4 尺寸、打印规则或 PDF 内容。

## 页面与源码映射

以下路径相对项目根目录。

| 路由 / 范围 | 修改文件 | 变化 |
| --- | --- | --- |
| 公共工作台、设置、抽屉等继承的公共控件 | `frontend/src/style.css`、`frontend/src/shared/ui/AppChrome.vue` | 语义变量、辅助文字对比度、公共标题/按钮/表单、稳定导航、安全区 |
| `/career-library` 的画像、记录、文件 | `features/career-library/career-library.css`、`pages/CareerLibraryPage.vue`、`components/CareerProfileTab.vue` | 统一蓝色、表单分组、字号、合并完整度/待补全/已有保存状态，移除重复百分比 |
| `/career-planning` | `features/career-planning/pages/CareerCanvasOverviewPage.vue` | 重写源 CSS，移除 500/610px 覆盖链；卡片 min-height 300px、内容自然增高；新建入口为紧凑独立操作行；统计横条 |
| `/career-planning/new`、`/career-planning/:sessionId` | `features/career-planning/career-planning.css` | 画像、方向推荐、确认、授权、访谈表单的颜色、字号、面板和控件 |
| 画布/学习计划/验证/版本及相关弹窗 | `components/CareerPlanningWorkbench.vue`、`CareerAbilityCanvas.vue`、`CareerLearningPlan.vue`、`CareerValidationWorkspace.vue`、`CareerVersionHistory.vue`、`CareerSkillPicker.vue`、`CareerPlanSetupDialog.vue`、`CareerProposalReview.vue` | 仅样式修改；保留引擎、节点方向、选中状态、移动分层列表、真实数据与版本独立性 |
| `/ai-resume/:conversationId` | `features/ai-resume/workbench-studio.css`（由全局样式引入） | 明确限定 `.ai-workbench` 界面层：对话、建议比较、待确认卡、输入区和预览工具栏；不触碰纸张内容 |
| `/job-match`、new/analyzing/clarifications/report/history/similar-jobs | `features/job-match/job-match.css` | 紧凑统计、统一动作色、报告/证据辅助字号、稳定列表悬停、移动触达 |
| `/mock-interviews` 及共享控件 | `features/mock-interview/mock-interview.css`、`pages/MockInterviewHomePage.vue` | 精简文字/语音入口的图标面积；统计、记录、标题和按钮统一 |
| `/resume-templates` | `features/resume/pages/ResumeTemplateCatalogPage.vue` | 施工状态改为紧凑面板；保留原标题；移除开发进度装饰；保留已有浏览、预览及下载入口 |
| `/notifications` | `features/notification/pages/NotificationListPage.vue` | 连续轻边界列表；保留未读标记、类型和来源动作；移除“服务端分页查询” |
| `/updates`、`/updates/:version` 及共享更新组件 | `features/updates/updates.css` | 标题与工具栏回归工作台尺度；时间线保持，降低卡片悬停阴影 |

表中未写 `frontend/src/` 的路径均位于该目录；职业规划组件均位于 `features/career-planning/`。

## 可复验检查

- 已执行 `node docs/visual/check-workbench-styles.mjs`：22 个修改文件的 CSS、Vue 模板与 scoped 样式解析通过。
- 同一脚本断言 AI 工作台新增样式所有规则均限定页面类，且不包含纸张或分页选择器。
- 原施工标题保持“智能编辑正在施工”，避免改变已有产品文案约定与组件断言。
- 完整构建、组件测试和 390/768/1440/1920px 截图由主任务统一执行；本清单不替代其截图和交互结果。

## 视觉验收清单

四尺寸主要页面、长文本/表单、下拉/文件夹、移动导航和有数据简历分页共 85 个唯一场景通过。还修复了弹层按钮颜色、月份日期精度、768px 资料筛选栏和有数据简历列表溢出。下列要求用于持续回归，不代表所有业务状态已覆盖。

- 长标题和真实多行经历、完整度/未保存/错误状态、弹窗与下拉打开时无截断；页面与抽屉不横向溢出。
- 画布有足够内容时允许高于 300px；不得为了固定高度隐藏目标、进度、当前任务或版本。
- AI 正式内容与待确认建议清楚区分；对比修改前后相同内容的 A4 字体、行数与分页。
- 390px 的主要动作、列表操作和导航可触达；安全区不遮挡底部输入与抽屉。
- 键盘聚焦、减少动态、加载/失败/禁用、空数据仍可识别；未读与业务状态不能仅靠颜色表达。

## 未扩大的范围

未新建图编辑引擎、业务流程、职业数据或任何薪酬内容。未新增“生长”业务状态或伪造验证结果；已有真实确认/分支展开动效见最新验收。249 个 Sprite 字形与字体引用已追加[专门核验](2026-09-06-icons-and-fonts.md)，没有可安全删除的发布字体；12 套模板的短合成 PDF 已导出并渲染检查。任意长简历分页和真实录音硬件仍不在该结论内。已有无关样式规则不做整库重构。
