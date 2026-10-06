# AI 职业规划逐图视觉验收矩阵

## 2026-08-28 职业规划 UI 与切换动效精修复核

- 工作台现使用 `能力画布 / 学习计划 / 能力验证 / 版本记录` 四个标准 ARIA 页签，以及移动端 `对话 / 画布 / 计划 / 验证 / 版本` 五项明确导航。
- 四个工作台视图使用缓存视图、URL 恢复、前后方向、独立滚动位置和 `180–220ms` 局部过渡；`prefers-reduced-motion` 下取消位移与平滑动画。
- 学习计划原为 Fragment 根节点，无法参与 Vue `Transition`，会在“计划 → 验证”时留下空白帧。现已改为单一 `tabpanel` 根节点，最小浏览器复现确认面板正常挂载且 Vue Transition 警告为 `0`。
- `1015x898` 推荐页的“推荐说明”网格已改为可收缩轨道，实测 `documentElement.scrollWidth` 从 `1028` 降为 `1015`，不再产生横向滚动。
- 当前验收视口为 `1440x900`、`1015x898`、`1280x800`、`390x844`、`414x896`；截图目录为 `.codex_tmp/career-planning-acceptance-current/{project}/{state}.png`。
- 前端逻辑测试当前基线为 `122/122`，组件测试为 `21/21`；组件覆盖学习计划单根节点与 ARIA 属性回归，生产构建通过。
- 本轮缺陷截图为 `.codex_tmp/career-planning-acceptance-current/desktop-1015/recommendations-fixed.png` 与 `validation-transition-fixed.png`；两页均实测 `scrollWidth === clientWidth === 1015`，控制台错误为 `0`。
- 聚焦真实流程已通过 `desktop-1440` AI 访谈和 `desktop-1015` AI 访谈。完整纵向闭环曾进入能力验证并用于复现切换故障；后续复跑有真实供应商返回节点数量不满足“标准 22–44 节点”配置，服务端按质量门禁拒绝落库，因此该真实 AI 用例具有外部输出非确定性，不能计入稳定 UI 回归通过数。
- 后端经 `mvn clean compile` 后重启，修复了长驻开发 JVM 与增量编译内部类不一致导致的 `NoClassDefFoundError`；本次未修改职业规划后端 API、数据库或业务规则。

## 验收口径

- 视觉基准：38 张 PNG，清单 SHA-256 `CD55587CFE371687830155A8FD414601FE914A337A69CAD97610D555B5679E12`。
- 目标视口：桌面 `1440x900`、`1280x800`；移动端 `390x844`、`414x896`。
- `NOT_RUN`：尚无同视口截图对照；代码存在不等于通过。
- `FAILED`：已对照并发现阻断差异。
- `PASSED`：功能、视觉、键盘、控制台和恢复状态均有证据。
- 每项通过前必须填写：实现链接、自动化测试、当前截图、差异结论和复核时间。
- 当前原始规范目录未固化到仓库；恢复原图后先校验清单 SHA-256，再执行逐图对照。

## 2026-08-28 交付复核

- 功能与响应式自动化：`frontend/e2e/career-planning.spec.ts` 在 `desktop-1440`、`desktop-1280`、`mobile-390`、`mobile-414` 共 `20/20` 通过，耗时 8.8 分钟，零跳过。
- 纵向闭环实际覆盖：画像建档与恢复、资料授权、AI 访谈、方向推荐、收藏对比、目标双确认、完整能力树、结构化节点编辑、AI 差异提案、学习计划、证据、能力验证、版本恢复和独立能力画布新建。
- 当前实现截图：每个视口 20 个状态，共 80 张，固定保存在 `.codex_tmp/career-planning-acceptance-current/{project}/{state}.png`。
- 后端完整测试：`297` 项，`0` 失败、`0` 错误、`1` 跳过；跳过项是真实 Word 预览集成开关，与职业规划无关。
- 前端：逻辑测试 `117/117`、组件测试 `8/8`、生产构建通过。
- 运行日志：职业规划 E2E 后没有 `AsyncRequestNotUsableException` 或 `GlobalExceptionHandler: unhandled error`。
- 规范图视觉结论仍为 `0/38 PASSED`。当前 80 张截图只能证明当前实现状态稳定，原始 38 张 PNG 缺失时不能进行像素、间距、字号、颜色和状态一一对照。

## 2026-08-28 多能力画布决策与新增视觉基准

- 新增权威参考图：`codex-clipboard-0a1cd865-84ba-42bd-b690-76f8005c4af1.png`，尺寸 `1920x1080`，SHA-256 `9FF1FF32530A0DB694B9DEC511F8008F1157D3581CDD359A5B9ACECC7867842A`。
- 一个求职者可以拥有多个相互独立的能力画布；每张画布独立保存学习计划、能力验证和版本记录，只有一个画布可标记为主目标。
- 新建画布从完整岗位分类中选择目标，先创建 `v1` 单根节点，再由用户触发 AI 生成全新的完整能力树；旧画布和节点不得复制或归档。
- 原 `V04-05` 与 `M05-16` 的“更换目标职业、归档和复用节点”规范已被本决策取代，不再作为产品功能或验收要求。
- 新总览已在 `1440x900` 和 `390x844` 实机验证：多画布卡片、统计、岗位选择、节点搜索、根节点生成入口、无旧步骤条、无横向溢出、控制台零错误。

## 总览

| 分组 | 数量 | 已通过 | 未执行 |
| --- | ---: | ---: | ---: |
| 整体流程 | 1 | 0 | 1 |
| 桌面状态 | 21 | 0 | 21 |
| 移动端状态 | 16 | 0 | 16 |
| 合计 | 38 | 0 | 38 |

## 整体流程与桌面状态

| ID | 参考图 | 必须验证 | 当前实现入口 | 自动化证据 | 截图证据 | 状态 |
| --- | --- | --- | --- | --- | --- | --- |
| V00-01 | `00_整体流程/01_AI职业规划完整流程总览.png` | 全流程步骤、分支、回路、恢复和版本生命周期 | `CareerPlanningPage.vue`、`CareerPlanningWorkbench.vue` | 待补 E2E | 无 | NOT_RUN |
| V01-01 | `01_入口与职业画像/01_职业规划欢迎与双入口.png` | 双入口、最近进度、AI 不代选 | `CareerPlanningPage.vue` | 待补组件/E2E | 无 | NOT_RUN |
| V01-02 | `01_入口与职业画像/02_职业画像基础信息_结构化表单.png` | 分区表单、草稿校验、刷新恢复 | `CareerPlanningPage.vue` | 待补组件/E2E | 无 | NOT_RUN |
| V01-03 | `01_入口与职业画像/03_技能标签下拉与自定义录入.png` | 搜索、分类、多选、自定义和状态标签 | `CareerSkillPicker.vue` | `skillPicker.test.ts`，待补组件/E2E | 无 | NOT_RUN |
| V01-04 | `01_入口与职业画像/04_个人倾向与工作限制_表单.png` | 偏好、限制、可跳过项和禁止薪酬项 | `CareerPlanningPage.vue` | 后端策略单测，待补 UI | 无 | NOT_RUN |
| V01-05 | `01_入口与职业画像/05_资料授权弹窗_选择证据.png` | 默认关闭、逐项范围和撤销 | `CareerPlanningPage.vue` | `CareerPlanningIT` 部分覆盖 | 无 | NOT_RUN |
| V01-06 | `01_入口与职业画像/06_AI补充访谈_事实与推断.png` | 对话、问题卡、事实类型和冲突确认 | `CareerPlanningPage.vue` | `CareerPlanningIT` Mock AI | 无 | NOT_RUN |
| V01-07 | `01_入口与职业画像/07_职业画像确认_编辑态.png` | 逐项编辑、删除、来源回看和快照确认 | 当前只有逐项勾选，待补齐 | `CareerPlanningIT` 部分覆盖 | 无 | NOT_RUN |
| V02-01 | `02_职业方向推荐/01_职业方向推荐_多选项.png` | 3-6 项、筛选、并排比较和收藏 | `CareerPlanningPage.vue`，包含三项并排比较与收藏 | `comparison.test.ts`、`CareerPlanningIT` 部分覆盖 | 无 | NOT_RUN |
| V02-02 | `02_职业方向推荐/02_职业详情抽屉_推荐依据.png` | 依据、差距、能力匹配和来源 | `CareerPlanningPage.vue` 详情弹窗 | 待补组件/E2E | 无 | NOT_RUN |
| V02-03 | `02_职业方向推荐/03_目标职业确认弹窗.png` | 影响说明、confirmationToken 和二次确认 | `CareerPlanningPage.vue` | `CareerPlanningIT` 覆盖 | 无 | NOT_RUN |
| V02-04 | `02_职业方向推荐/04_资料不足_补充与重新推荐.png` | 缺项、补充、重试且不硬凑推荐 | `CareerPlanningPage.vue` | 待补真实不足场景 | 无 | NOT_RUN |
| V03-01 | `03_职业能力画布/01_Java全栈职业能力树_完整画布.png` | 左侧 AI、中间 RL 画布、检查器和工具栏 | `CareerAbilityCanvas.vue` | 真实生成已跑通，待 E2E | `tmp/career-planning-review-1440x900.png`（诊断） | NOT_RUN |
| V03-02 | `03_职业能力画布/02_节点详情抽屉_结构化编辑.png` | 类型、状态、描述、证据、关系和锁定 | `CareerAbilityCanvas.vue` | `CareerPlanningIT` 覆盖核心写入 | 无 | NOT_RUN |
| V03-03 | `03_职业能力画布/03_节点操作菜单_新增拆分移动.png` | 新增、拆分、移动、合并、锁定和删除影响预览 | `CareerAbilityCanvas.vue`，支持新增、拆分、移动、合并、锁定、批量更新和删除 | `CareerPlanningIT` 覆盖核心写入，待补组件/E2E | 无 | NOT_RUN |
| V03-04 | `03_职业能力画布/04_AI建议节点_差异确认.png` | 增删改移、逐项/批量决策和锁定隔离 | `CareerProposalReview.vue` | 真实提案已跑通，待 E2E | 无 | NOT_RUN |
| V03-05 | `03_职业能力画布/05_学习路径聚焦_依赖关系.png` | 父链、前置链、降噪、说明和环检测 | `CareerAbilityCanvas.vue` | 纯函数与后端环检测覆盖 | 无 | NOT_RUN |
| V04-01 | `04_学习计划与版本/01_计划周期选择弹窗_4_8_12周.png` | 4/8/12 周、自定义、时间投入和容量校验 | `CareerPlanSetupDialog.vue` | `CareerPlanningIT` Mock 流程 | 无 | NOT_RUN |
| V04-02 | `04_学习计划与版本/02_每周行动计划_任务与证据.png` | 周任务、状态、证据、重排、锁定和修订 | `CareerLearningPlan.vue`，支持同周排序和跨周移动 | `CareerPlanningIT` 覆盖状态、证据、修订和重排 | 无 | NOT_RUN |
| V04-03 | `04_学习计划与版本/03_能力验证_专项测试与证据.png` | 验证方法、AI 候选、证据和人工确认 | `CareerValidationWorkspace.vue`，提供专项、项目、场景、代码和证据验证 | `CareerPlanningIT` Mock AI | 无 | NOT_RUN |
| V04-04 | `04_学习计划与版本/04_规划版本历史抽屉.png` | 版本原因、比较、恢复为新版本 | `CareerVersionHistory.vue` | `CareerPlanningIT` 覆盖恢复 | 无 | NOT_RUN |
| V04-05 | `04_学习计划与版本/05_更换目标职业_归档与复用.png` | 已被“每个目标独立一张能力画布”决策取代 | 无，相关生产代码与接口已删除 | 旧接口返回 404 | 无 | SUPERSEDED |
| V06-01 | `codex-clipboard-0a1cd865-84ba-42bd-b690-76f8005c4af1.png` | 多画布统计、卡片、搜索筛选、新建入口、主目标、计划/验证/版本归属 | `CareerCanvasOverviewPage.vue` | `CareerPlanningIT`、`CareerCanvasOverviewPage.component.test.ts`、Playwright | `.codex_tmp/career-planning-acceptance-current/*/canvas-overview.png` | PASSED |

## 移动端状态

| ID | 参考图 | 必须验证 | 当前实现入口 | 自动化证据 | 截图证据 | 状态 |
| --- | --- | --- | --- | --- | --- | --- |
| M05-01 | `05_移动端/01_职业推荐_移动端.png` | 单列推荐、筛选、比较和底部导航 | 响应式列表，待独立状态验收 | 无 | 无 | NOT_RUN |
| M05-02 | `05_移动端/02_能力树分层列表_移动端.png` | 分层、展开、状态、搜索、虚拟化和滚动恢复 | `CareerAbilityCanvas.vue` 移动虚拟列表，支持批量选择与滚动恢复 | `canvas.test.ts` 虚拟窗口测试，待组件/E2E | `tmp/career-planning-audit-390x844.png`（历史诊断） | NOT_RUN |
| M05-03 | `05_移动端/03_职业规划欢迎与双入口_移动端.png` | 双入口、sticky CTA 和安全区 | `CareerPlanningPage.vue` | 无 | 无 | NOT_RUN |
| M05-04 | `05_移动端/04_职业画像基础信息_移动端.png` | 单列分组、进度、键盘和草稿恢复 | 响应式画像表单 | 无 | 无 | NOT_RUN |
| M05-05 | `05_移动端/05_技能选择底部弹层_移动端.png` | 搜索、多选、自定义、已选区和焦点陷阱 | 尚未实现 | 无 | 无 | NOT_RUN |
| M05-06 | `05_移动端/06_资料授权底部弹层_移动端.png` | 证据范围、隐私、默认关闭和逐项授权 | 当前通用弹窗，待移动 Sheet | 无 | 无 | NOT_RUN |
| M05-07 | `05_移动端/07_AI补充访谈_移动端.png` | 消息、问题卡、即时入列和断线恢复 | 当前表单式访谈，待消息流 | 无 | 无 | NOT_RUN |
| M05-08 | `05_移动端/08_职业画像确认_移动端.png` | 来源、编辑、隔离未确认项和 CTA | 当前勾选卡，待补齐 | 无 | 无 | NOT_RUN |
| M05-09 | `05_移动端/09_职业详情底部抽屉_移动端.png` | 可滚动 Sheet、来源、焦点陷阱和安全区 | 当前通用弹窗，待移动 Sheet | 无 | 无 | NOT_RUN |
| M05-10 | `05_移动端/10_目标职业确认弹窗_移动端.png` | 防误触、影响说明和幂等确认 | 通用确认弹窗，待逐图验收 | 无 | 无 | NOT_RUN |
| M05-11 | `05_移动端/11_节点详情结构化编辑_移动端.png` | 结构化字段、冲突和返回定位 | `CareerAbilityCanvas.vue` 底部抽屉 | 无 | 无 | NOT_RUN |
| M05-12 | `05_移动端/12_每周行动计划_移动端.png` | 周折叠、任务状态、证据和完整操作 | `CareerLearningPlan.vue` | 无 | 无 | NOT_RUN |
| M05-13 | `05_移动端/13_计划周期选择底部弹层_移动端.png` | 周期、可用时间和 sticky 确认 | 当前通用弹窗，待移动 Sheet | 无 | 无 | NOT_RUN |
| M05-14 | `05_移动端/14_能力验证_移动端.png` | 方法、提交、候选、人工确认和重试 | `CareerValidationWorkspace.vue` | 无 | 无 | NOT_RUN |
| M05-15 | `05_移动端/15_规划版本历史_移动端.png` | 分页、摘要、比较和恢复新版本 | `CareerVersionHistory.vue` | 无 | 无 | NOT_RUN |
| M05-16 | `05_移动端/16_更换目标职业_移动端.png` | 已被独立画布新建流程取代 | 无，相关生产代码与接口已删除 | 旧接口返回 404 | 无 | SUPERSEDED |

## 当前阻断差异

1. 原始 38 张参考图尚未固化到仓库，无法进行像素级复核。
2. 当前自动化固定了 20 个真实产品状态，并在四个视口生成 80 张截图；它们尚未与规范中的 38 个编号建立逐图一一映射。
3. 恢复原图后必须先核对清单 SHA-256，再逐张产出基准图、当前图、差异图和复核结论；在此之前不得把任何规范项改为 `PASSED`。
4. 现有表格中标注“待补”的历史说明将随原图恢复后的逐项对照一起更新，顶部复核结果是当前自动化事实。
