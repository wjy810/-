# JobProof AI P0A S3 前端交付说明

> 日期：2026-08-18
> 角色：S3 前端切片官（简历工作台 / 投递 CRM）
> 编码：UTF-8
> 技术：Vue 3 + TypeScript + Vite。禁止用 Python 跑本项目。
> 性质：本刀可运行页面 + 契约消费说明 + 未做事项。**不是**面试轮次详情或复盘分析页。未 git commit。未改 backend Java。

圣旨已放权且禁止询问。沿用 S0/S1/S2「验真案牍 / Proof Folio」：暖纸、蜡印、衬线标题。elite-web-designer 未走 Phase 1–6 交互问询与生图；本刀只把同一套案卷语言接到简历与投递，不另起第二套页面。

## 1. 本切片交付了什么

工程仍在 `frontend/`。开发时 Vite 把 `/api` 与 `/internal` 代理到 Spring Boot `http://127.0.0.1:8080`。浏览器只打 `http://localhost:5173`，Cookie `jobproof_session`，`credentials: 'include'`。

**未改 backend Java。** 只消费 S3 已确认契约。`npm run build`（`vue-tsc -b && vite build`）本机已通过。

### 已接操作（只消费 S3 已确认契约）

| 能力 | 方法 | 路径 | 页面表现 |
| --- | --- | --- | --- |
| 创建主档（空白 / 模板 / 导入） | POST | `/api/v1/resumes` | `/resumes`；模板四码；导入入候选，主档待确认 |
| 主档列表 | GET | `/api/v1/resumes` | 未归档 / 状态筛选 |
| 复制 / 归档 / 恢复 | POST | `/resumes/{id}/copy\|archive\|restore` | 副本为新草稿、不带投递；归档可恢复；无物理删除按钮 |
| 编辑正式字段 | PUT | `/api/v1/resumes/{id}` | 教育、经历、项目、技能、证书、自我介绍、关键成果 |
| 标可投递 / 冻结 | POST | `/resumes/{id}/ready`、`/freeze` | 未确认 AI 或未处理成果时按钮禁用并说明；服务端 409 仍展示 |
| 候选确认 / 拒绝 / 更正 | POST | `/resumes/{id}/candidates/{cid}/confirm\|reject\|correct` | 拒绝项非正式事实 |
| 关联证据 / 暂无证据仍要冻结 | POST | `/outcomes/{oid}/evidence`、`/waive` | waive 须勾选 `confirmed=true` |
| 确认定制版本并冻结 | POST | `/resumes/versions/{id}/confirm` | 仅 `PENDING_USER_CONFIRMATION`；不得由任务自动冻结 |
| 归档版本 | POST | `/resumes/versions/{id}/archive` | BOUND 时提示归档不解除绑定 |
| 版本对比 | GET | `/resumes/versions/{id}/compare?with=` | 字段对照，差异高亮 |
| 导出 PDF 任务 | POST | `/resumes/versions/{id}/export-pdf` | 仅已冻结 / 已绑定；轮询至成功。任务查询不含 fileId，页面展示任务成功而非浏览器直链下载 |
| 准备投递 / 记录已投递 | POST | `/api/v1/applications/prepare`、`/record` | 下拉只列 FROZEN/BOUND 简历 + CONFIRMED 岗位 |
| 投递列表 / 详情 / 时间线 | GET | `/applications`、`/{id}`、`/{id}/timeline` | 看板与详情 |
| 改渠道 / 备注 / 备注公司名 | PUT | `/api/v1/applications/{id}` | 不改岗位快照 |
| 前进 | POST | `/{id}/advance` | 仅 `allowedForward` 可点 |
| 更正 | POST | `/{id}/correct` | 必须填原因；目标取 `correctTarget` |
| 接受后撤回 | POST | `/{id}/withdraw-after-accept` | 二次确认 + 展示影响 |
| 归档投递 | POST | `/{id}/archive` | 明确提示不解除版本绑定 |

错误类别原样展示服务端 `message` / `reason`。401 且原先已登录 → 回登录页。403 `OPERATOR_NO_ORIGINAL` / `OBJECT_FORBIDDEN` → 封存页。`VERSION_CONFLICT` 拒绝覆盖并重读。

### 简历 / 投递规则（未发明）

- AI 只出候选。未确认禁止标可投递、禁止冻结。
- 关键成果要么关联 ACTIVE 证据，要么逐条确认「暂无证据仍要冻结」。不能默认放行。
- 只有主档为 `可投递` 才能冻出 `已冻结`。冻结后不可原地改；要改必须再冻一份。
- 创建投递必须绑本人 `FROZEN`/`BOUND` 简历版本 + `CONFIRMED` 岗位版本。匹配报告不是前置。
- 合法前进按服务端 `allowedForward`。非法流转禁用，并写明当前态与允许去向。
- 更正须填原因，不能跳步。`Offer已接受` 不能退回或并入主动放弃，只能接受后撤回（二次确认）。
- 归档不解除已绑定简历版本。P0A 无物理删除按钮、无批量改状态、无收藏、无漏斗。
- 进入面试中不强制建轮次，可提示 S4。本刀不打开轮次详情或复盘页。
- 岗位确认页 / 匹配报告「建立投递」已解禁，接到 `/applications/new?jobVersionId=`。仍不会自动建投递。

### 工作台

顶栏增加「简历」「投递」。工作台「当前简历版本 / 当前投递进度」可导航到本刀页面。`href` 仍显示来源契约；已映射的再给「打开页面」。简历定制 / PDF 导出任务链到 `/resumes`，不再误进岗位导入页。

## 2. 页面清单（同一套路由，不两套）

| 路由 | 谁能进 | 做什么 |
| --- | --- | --- |
| `/` | 皆可 | 已登录 → 工作台；否则 → 登录 |
| `/login` `/register` `/reset` | 仅游客 | S0 会话 |
| `/workspace` | 须登录 | 十块只读；可去画像 / 证据 / 岗位 / 简历 / 投递 |
| `/profile` | 须登录 | S1 画像 |
| `/evidences` `/evidences/:id` | 须登录 | S1 证据 |
| `/jobs` `/jobs/:jobId` `/jobs/versions/:versionId` | 须登录 | S2 岗位；确认后可建投递 |
| `/matching/current` `/matching/reports/:id` | 须登录 | S2 报告；可建投递，不自动创建 |
| `/resumes` | 须登录 | 简历中心：创建（空白/模板/导入）、列表、复制、归档、恢复 |
| `/resumes/:id` | 须登录 | 编辑器：正式字段、候选、成果闸、可投递、冻结、对比、导出 PDF |
| `/resumes/versions/:versionId` | 须登录 | 收口到 `/resumes/:id?versionId=`，不是第二套页 |
| `/applications` | 须登录 | 投递看板 |
| `/applications/new` | 须登录 | 准备投递 / 记录已投递 |
| `/applications/:id` | 须登录 | 合法前进、更正填原因、接受后撤回、归档、时间线 |
| `/account` | 须登录 | 改密 |

未登录访问须登录路由 → `/login?reason=unauthenticated&next=…`。

若巡更或其他切片也写简历 / 投递页，必须以本表路由为准，不得再开 `/crm`、`/resume-board` 等平行页面。

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

1. 登录 → 工作台 → 打开简历，或顶栏「简历」。
2. 空白 / 模板创建进入编辑器；导入文本后主档为待确认，候选须处理。
3. 不处理候选去点「标为可投递」：按钮禁用并说明未确认 AI。
4. 增加关键成果并保存 → 不关联证据且不勾选暂无证据 → 不能标可投递 / 冻结。
5. 关联证据或逐条确认暂无证据 → 标可投递 → 冻结。冻结版本可对比；仅冻结/已绑定可导出 PDF。
6. 复制主档得到新草稿，原投递仍绑旧版本。归档可恢复。看不见物理删除。
7. 打开已确认岗位或匹配报告 →「建立投递」→ 必须再选冻结简历版本。草稿或未确认岗位不在下拉中。
8. 准备投递得到待投递；记录已投递得到已投递。待投递直接点「面试中」禁用并说明允许去向。
9. 更正须填原因。Offer已接受只能接受后撤回，须勾选二次确认。
10. 归档投递时看到「不解除已绑定简历版本」。进入面试中有 S4 轮次提示，本页不建轮次。

## 4. 状态覆盖

| 态 | 何处可看 |
| --- | --- |
| 正常 | 冻结版本出现在列表；投递详情可前进 |
| 加载 | 简历/投递骨架；按钮「正在…」 |
| 空 | 无主档、无冻结版本、无投递的斜体说明 |
| 错误 | 后端未接通开机页；接口 `message`；`VERSION_CONFLICT` 拒绝覆盖并重读 |
| 校验 | 导入无文本不提交；更正无原因不提交；waive 未勾选不提交 |
| 提交中 | 创建/保存/冻结/前进等按钮禁用 |
| 403 | `ForbidState` |
| 禁用 | 未确认不能可投递/冻结；非法流转禁用并说明；未冻结不能导出 PDF；未确认岗位不能建投递 |
| 归档 | 主档可恢复；投递归档提示绑定仍在 |
| 任务 | PDF 导出 PENDING/RUNNING 轮询；成功可见任务态 |

## 5. 未做事项

本刀**故意不做**：

- 面试轮次详情、复盘分析页（S4）
- 按已确认 JD 发起定制任务的独立按钮（确认记录 10.2 未列；若已有 `PENDING_USER_CONFIRMATION` 版本，编辑器可确认后冻结）
- PDF 浏览器直链下载（`TaskView` 不含 payload/fileId；本地存储 `local://` 也不能在浏览器打开）
- 收藏、漏斗、批量改状态、批量导入投递、自动建投递
- 简历 / 投递物理删除（走数据权利）
- 覆盖已绑定版本、未确认就标可投递
- 最小通知、数据权利（仍属 S0 尾巴）
- 手机号、第三方、MFA、运营后台
- P0B / P1 / P2、App / 扩展
- git commit
- 改 backend；全量 `mvn test` 不在本刀修

## 6. 验收门对照

| 门 | 本刀 |
| --- | --- |
| 确认记录 10.2 简历中心 | `/resumes` 创建、导入、复制、归档、恢复、查看；无物理删除 |
| 确认记录 10.2 简历编辑器 | `/resumes/:id` 正式字段、候选、证据/暂无证据、可投递、冻结、对比、导出 PDF |
| MOCK-RESUME-002 未确认 AI | 按钮禁用 + 横幅；提交仍会展示 `UNCONFIRMED_AI_FACTS` |
| MOCK-RESUME-003 无证据须逐条确认 | 未勾选不能 waive；未处理不能可投递/冻结 |
| MOCK-RESUME-004 已绑定不可覆盖 | 版本标不可变；无原地改版本入口 |
| MOCK-CRM-002 无冻结或岗位未确认 | 创建页下拉为空并说明缺什么 |
| MOCK-CRM-003 非法流转 | 禁用并说明当前态与允许去向 |
| MOCK-CRM-004 Offer已接受 | 只能接受后撤回，二次确认 |
| MOCK-CRM-006 归档不解除绑定 | 归档确认文案写明绑定仍在 |
| 进入面试中不强制建轮次 | 前进至面试中后提示 S4；详情无轮次表单 |
| 工作台可导航至本刀 | 当前简历 / 当前投递 / 简历候选待办 href 已映射 |
| 无收藏、无漏斗、无批量改状态 | 无入口 |

## 7. 目录

```txt
frontend/src/features/resume/          简历中心、编辑器、候选、版本收口
frontend/src/features/application/     投递看板、创建、详情流转
frontend/src/shared/lib/workspaceNav.ts  映射 resumes / applications / RESUME_* 任务
frontend/src/app/router.ts             本刀路由（versions 与 new 写在 :id 之前）
frontend/src/shared/ui/AppChrome.vue   顶栏「简历」「投递」
```

## 8. 回滚

删除 `features/resume`、`features/application`，还原 `router.ts`、顶栏、工作台链接、岗位/报告「建立投递」按钮与 `workspaceNav.ts`，即撤回本刀页面。勿用本页字段倒逼改契约。
