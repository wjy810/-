# JobProof AI P0A S1 前端交付说明

> 日期：2026-08-18
> 角色：S1 前端切片官（画像 / 证据）
> 编码：UTF-8
> 技术：Vue 3 + TypeScript + Vite。禁止用 Python 跑本项目。
> 性质：本刀可运行页面 + 契约消费说明 + 未做事项。**不是** JD/匹配页、简历编辑器或投递看板。未 git commit。未改 backend Java。

圣旨已放权且禁止询问。沿用 S0「验真案牍 / Proof Folio」：暖纸、蜡印、衬线标题。elite-web-designer 未走 Phase 1–6 交互问询与生图。

## 1. 本切片交付了什么

工程仍在 `frontend/`。开发时 Vite 把 `/api` 与 `/internal` 代理到 Spring Boot `http://127.0.0.1:8080`。浏览器只打 `http://localhost:5173`，Cookie `jobproof_session`，`credentials: 'include'`。

**未改 backend Java。** CORS 已含 5173，本刀继续走代理。`fetch` 对 `FormData` 不再强行加 `Content-Type: application/json`，以便证据附件走 S0 `POST /api/v1/files`。

### 已接操作（只消费 S1 已确认契约）

| 能力 | 方法 | 路径 | 页面表现 |
| --- | --- | --- | --- |
| 读正式画像 | GET | `/api/v1/profile` | `/profile` 完整度、来源、乐观锁版本 |
| 写正式事实（合并；空值清除） | PUT | `/api/v1/profile/facts` | 必填方向、建议六项、可后补技能/年限/兴趣/薪资/自我介绍 |
| 匹配前门 | POST | `/api/v1/profile/matching-gate` | 无方向展示 `DIRECTION_NOT_CONFIRMED`，不发起 JD 匹配 |
| 候选列表 | GET | `/api/v1/profile/candidates` | 分页；拒绝项带「非正式事实」印记 |
| 登记候选（本刀不跑模型） | POST | `/api/v1/profile/candidates` | 仅 PENDING，确认前不进正式事实 |
| 确认 / 拒绝 / 更正 | POST | `/candidates/{id}/confirm\|reject\|correct` | 拒绝后不能再当履历引用 |
| 证据列表 | GET | `/api/v1/evidences` | 默认 ACTIVE；可切 ARCHIVED / ALL |
| 创建六类证据 | POST | `/api/v1/evidences` | 项目 / 实习工作 / 作品 / 证书 / 课程竞赛 / 自述 |
| 证据详情与更新 | GET/PUT | `/api/v1/evidences/{id}` | 已引用改正文禁用；备注可改 |
| 归档 / 恢复 / 复制 | POST | `.../archive\|restore\|copy` | 无物理删除按钮 |
| 引用检查 | GET | `/api/v1/evidences/{id}/reference-check` | 详情页展示冻结引用说明 |
| 本人文件上传 | POST | `/api/v1/files` | 证据核验附件，最大 1MB |

错误类别原样展示服务端 `message` / `reason`。401 且原先已登录 → 回登录页。403 `OPERATOR_NO_ORIGINAL` / `OBJECT_FORBIDDEN` → 封存页，不给半套表单。

### 画像规则（未发明）

- 匹配前必填：`JOB_DIRECTION`。空则保持未知，匹配前门失败。
- 建议补：最高学历、毕业时间、专业、目标城市、工作方式、现实限制（可写「无限制」）。缺失不补默认。
- 可后补：技能（熟练度可选）、年限、兴趣、薪资预期（底线/目标/理想 + 月薪或年包）、自我介绍。
- **薪资预期、兴趣不进匹配计分**；薪资 / 兴趣 / 自我介绍不计入完整度。页面上写明。
- 表单**不出现**：性别、年龄、民族、婚育、政治面貌、身份证、家庭住址。
- 完整度条只渲染服务端 `percent` / `directionConfirmed` / `filledKeys`，不在浏览器重算权重。
- 拒绝的候选用虚线底与蜡印标「非正式事实 · 不进匹配 / 简历」，不混进正式事实栏。

### 证据规则（未发明）

- 六类枚举与后端一致。弱证据标 **待补证**，文案禁止「能力不足」。
- `referenced === true`：类型 / 标题 / 正文 / 核心成果 / 链接 / 附件锁定，只许改备注；主 CTA 是「复制为新证据」。
- 归档可恢复。不提供对象级硬删。

### 工作台

顶栏与工作台十块可导航到 `/profile`、`/evidences`。`href` 仍显示来源契约；若已映射到本刀页面，多一个「打开页面」。**没有**收藏、漏斗、风险、推荐。

## 2. 页面清单

| 路由 | 谁能进 | 做什么 |
| --- | --- | --- |
| `/` | 皆可 | 已登录 → 工作台；否则 → 登录 |
| `/login` `/register` `/reset` | 仅游客 | S0 会话 |
| `/workspace` | 须登录 | 十块只读；可去画像 / 证据 |
| `/account` | 须登录 | 改密 |
| `/profile` | 须登录 | 正式画像 + 候选 + 完整度 + 匹配前门 |
| `/evidences` | 须登录 | 六类创建 + 列表 + 归档/恢复/复制 |
| `/evidences/:id` | 须登录 | 详情、备注、正文锁、复制新证据 |

未登录访问上述须登录路由 → `/login?reason=unauthenticated&next=…`。

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

1. 注册/登录 → 工作台 → 打开画像。
2. 不填方向，看完整度 0% 与匹配前门失败 `DIRECTION_NOT_CONFIRMED`。
3. 写入求职方向后再探测前门，应通过。
4. 建议项、技能、薪资（标明不进匹配）可后补；把已填字段清空再写入，即按未知清除，不补默认履历。
5. 登记一条候选 → 确认 / 拒绝 / 更正。拒绝条不得像正式履历。
6. 打开证据 → 创建六类之一 → 归档/恢复 → 复制。详情页在 `referenced` 时改正文控件禁用。

## 4. 状态覆盖

| 态 | 何处可看 |
| --- | --- |
| 正常 | 写入方向后完整度 ≥ 30%；创建证据出现在册 |
| 加载 | 画像/证据骨架；按钮「正在…」 |
| 空 | 无正式事实、无候选、无证据的斜体说明 |
| 错误 | 后端未接通开机页；接口 `message`；`VERSION_CONFLICT` 拒绝覆盖并重读 |
| 校验 | 证据标题必填；候选更正值必填；附件 >1MB 拒绝；未过校验不提交 |
| 提交中 | 写入/创建/确认/归档等按钮禁用 |
| 403 | `ForbidState`：运营无原文、他人对象 |
| 禁用 | 已引用证据正文控件锁死并说明须复制 |

## 5. 未做事项

本刀**故意不做**：

- JD 导入、岗位确认、匹配报告页、自动重算
- 简历编辑器 / 冻结 UI、投递看板、面试轮次、复盘
- 真实 AI 归纳（仅提供登记候选以便演示确认闸）
- 工作台写接口、卡片排序冻结、收藏 / 漏斗 / 风险 / 推荐
- 最小通知、数据权利（导出 / 删除申请）
- 证据物理删除、情景测评、导师书面反馈
- 采集敏感属性、用薪资计分
- 手机号、第三方、MFA、运营后台
- P0B / P1 / P2、App / 扩展
- git commit
- 改 backend；全量 `mvn test` 若因其他切片半成品失败，不在本刀修

## 6. 验收门对照

| 门 | 本刀 |
| --- | --- |
| MOCK-PROFILE-002 无求职方向不得进入正式匹配 | `/profile` 匹配前门失败可见，不跳转 JD 页 |
| MOCK-EVIDENCE-001 改已引用正文拒绝并提示复制 | 详情页锁正文；若仍提交，展示 409 `EVIDENCE_REFERENCED` |
| PRD 23.1-2 基础画像 + 至少一条可追溯证据 | 可写方向；可创建带链接/文件的证据 |
| 架构 19.2 候选/正式分离 | 拒绝候选不进正式 facts 栏 |
| 工作台无禁词入口 | 可导航画像/证据；无收藏漏斗 |

## 7. 目录

```txt
frontend/src/features/profile/     画像、候选、完整度、匹配前门
frontend/src/features/evidence/    六类证据、归档恢复复制、附件
frontend/src/features/identity/    S0 会话（本刀只改顶栏文案）
frontend/src/features/workspace/   只读十块 + 导航到本刀页面
frontend/src/shared/api/           Cookie fetch；FormData 不误标 JSON
frontend/src/app/router.ts         路由与守卫
```

## 8. 回滚

删除 `features/profile`、`features/evidence` 并还原 `router.ts`、顶栏与工作台链接，即撤回本刀页面。勿用本页字段倒逼改契约。
