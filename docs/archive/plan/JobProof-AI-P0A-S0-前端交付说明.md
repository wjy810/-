# JobProof AI P0A S0 前端交付说明

> 日期：2026-08-18
> 角色：S0 前端切片官
> 编码：UTF-8
> 技术：Vue 3 + TypeScript + Vite。禁止用 Python 跑本项目。
> 性质：本刀可运行页面 + 代理说明 + 未做事项。**不是**卡片布局冻结，**不是** S1–S4 领域页，**不是**正式 Mock 工程。未 git commit。

圣旨已放权且禁止询问。elite-web-designer 未走 Phase 1–6 交互问询与生图；页面按「验真案牍 / Proof Folio」语境直接出码（暖纸、蜡印、衬线标题，避开紫渐变与通用 AI 仪表盘）。

## 1. 本切片交付了什么

工程在 `frontend/`。开发时 Vite 把 `/api` 与 `/internal` 代理到 Spring Boot `http://127.0.0.1:8080`。浏览器只打 `http://localhost:5173`，Cookie `jobproof_session` 落在前端源站，`credentials: 'include'`。

**未改 backend Java。** CORS 已含 `http://localhost:5173` / `http://127.0.0.1:5173`，Cookie 为 HttpOnly + `SameSite=Lax` + `Path=/`。本刀走代理，不依赖跨源带 Cookie。

### 已接操作（只消费已确认契约）

| 能力 | 方法 | 路径 | 页面表现 |
| --- | --- | --- | --- |
| 注册 | POST | `/api/v1/auth/register` | `/register`；成功后立刻登录 |
| 登录 | POST | `/api/v1/auth/login` | `/login`；写 Cookie |
| 退出 | POST | `/api/v1/auth/logout` | 顶栏「退出」 |
| 登录态改密（全部会话失效） | POST | `/api/v1/auth/password/change` | `/account`；须勾选不可逆确认 |
| 申请重置验证码 | POST | `/api/v1/auth/password/reset/request` | `/reset` 第一步；不暴露邮箱是否存在 |
| 验证码确认改密 | POST | `/api/v1/auth/password/reset/confirm` | `/reset` 第二步 |
| 当前用户 | GET | `/api/v1/me` | 启动时对齐登录态 |
| 工作台只读汇总 | GET | `/api/v1/workspace` | `/workspace` 十块只读 |
| 开发邮箱（dev） | GET | `/internal/dev/mailbox/{email}` | 重置页「开发邮箱：读取验证码」 |

错误类别原样展示服务端 `message` / `reason`。401 且原先已登录 → 回登录页并提示会话失效。

### 十块只读（不锁卡片）

页面只渲染这十个键：`todos`、`profileCompleteness`、`evidenceCompleteness`、`recentJob`、`currentMatch`、`currentResumeVersion`、`currentApplications`、`upcomingInterviews`、`pendingReviews`、`tasks`。

- `ok=false`：展示 `reason` / `message`，**不补默认事实**。
- `present=false` 或空数组：合法空文案，不捏总分、岗位或待办。
- `href` 只显示为「来源契约」路径，**不是**跳去画像 / JD / 简历编辑器的按钮。
- 没有收藏、漏斗、风险、推荐入口。
- 当前阅读排列（待办置顶）**不是**冻结方案，后续切片可改。

空账号若后端派生 `PROFILE_DIRECTION` 待办，本页只读展示，不打开画像编辑。

## 2. 页面清单

| 路由 | 谁能进 | 做什么 |
| --- | --- | --- |
| `/` | 皆可 | 已登录 → 工作台；否则 → 登录 |
| `/login` | 仅游客 | 邮箱 + 密码登录 |
| `/register` | 仅游客 | 邮箱注册并登录 |
| `/reset` | 仅游客 | 申请验证码 → 确认改密 |
| `/workspace` | 须登录 | 十块只读工作台 |
| `/account` | 须登录 | 改密（二次确认，成功后全部会话失效） |

未登录访问 `/workspace` 或 `/account` → `/login?reason=unauthenticated&next=…`。

## 3. 怎么启动

两台进程，先后端后前端。不要用 Python。

```bash
# 终端 1 · 后端（默认 8080，dev profile，H2，开发邮箱开启）
cd backend
mvn spring-boot:run

# 终端 2 · 前端
cd frontend
npm install
npm run dev
```

浏览器打开：**http://localhost:5173**

健康检查（可直打后端或经代理）：`GET /api/v1/health`，`slice` 应为 `S5`。

生产构建（本机已跑通 `vue-tsc -b && vite build`）：

```bash
cd frontend
npm run build
npm run preview
```

`preview` 默认不是 5173，且**没有**开发代理。联调请用 `npm run dev`。

代理见 `frontend/vite.config.ts`：`/api`、`/internal` → `http://127.0.0.1:8080`。

### 本机联调口令约定

- 密码至少 8 位、至多 72 位，不能等于邮箱。
- 验证码 6 位；dev 可用重置页「开发邮箱」读取，或 `GET http://localhost:8080/internal/dev/mailbox/{email}`。
- 改密 / 验证码改密成功后，旧 Cookie 再打 `/api/v1/me` 或 `/api/v1/workspace` 应为 401，前端回到登录页。

## 4. 状态覆盖

| 态 | 何处可看 |
| --- | --- |
| 正常 | 注册/登录进入十块；有数据则展示服务端字段 |
| 加载 | 启动核验会话；工作台骨架；按钮「正在…」 |
| 空 | 各块 `present=false` / 空列表的斜体说明 |
| 错误 | 后端未启动的开机页；工作台整页失败；单块 `ok=false`；接口 `message` |
| 校验 | 邮箱格式、密码长度、两次不一致、验证码 6 位；未过校验不提交 |
| 提交中 | 登录/注册/重置/改密/退出按钮禁用 |
| 未登录跳转 | 直开 `/workspace` 回登录 |
| 危险确认 | `/account` 须勾选「全部会话立即失效」 |

## 5. 未做事项

本刀**故意不做**（等对应切片前端，或本刀圣旨未点名）：

- 画像编辑、证据六类写入、JD 导入、匹配发起、简历编辑器、投递流转、面试轮次写入、复盘确认
- 最小通知（查看 / 已读）、数据权利（导出 / 删除申请 / 分享）
- 工作台写接口、卡片排序冻结、快捷操作、收藏 / 漏斗 / 风险 / 推荐
- 手机号、第三方、MFA、邮件短信、运营后台
- 正式 Mock 数据工程、P0B / P1 / P2、App / 扩展
- 完整 CSRF 双提交、生产部署、真实 SMTP 收信 UI
- git commit

`href` 指向的领域 API 不会在本刀变成可点编辑页。

## 6. 验收门对照

| 门 | 本刀 |
| --- | --- |
| MOCK-AUTH-001 邮箱登录 / 改密后旧会话失效 | `/login` + `/account`；改密后回登录，工作台须重新登录 |
| 邮箱验证码重置；错码拒绝 | `/reset`；错码展示 `RESET_CODE_INVALID` |
| 工作台十块只读、无禁词入口 | `/workspace` 只读十块；无漏斗/风险/推荐/收藏 |
| 未登录 | 工作台守卫跳转 |
| 不接未交付写入页 | 无画像/JD/简历编辑路由 |

## 7. 目录（过门后的 feature 拆法）

```txt
frontend/src/features/identity/   登录、注册、重置、改密、会话
frontend/src/features/workspace/  只读工作台
frontend/src/shared/api/          Cookie fetch 与错误语义
frontend/src/app/router.ts        路由与守卫
```

## 8. 回滚

删掉 `frontend/` 即撤回本刀页面，不影响 backend 102 测。勿用本页字段倒逼改契约。

## 9. 本轮硬化（巡更当值官，2026-08-18 第 6 次 tick）

开场时 S1 前端交付说明未落盘，本轮只硬化 S0 会话页，未开 JD / 匹配报告。收尾时 S1 说明已在，S2 留给下一刀。

- 登录后 `next` 只接受同源相对路径；拒绝 `//`、反斜杠、协议、空白、`..` 跳出与登录/注册/重置循环。
- `GET /api/v1/me` 会话核验并发只跑一次，后来者等待同一 Promise。
- 会话失效回登录时，保留安全的 `next`（例如从 `/account` 失效后仍回改密页）。
- 登录 / 注册 / 重置 / 改密表单加 `novalidate`，校验文案走 Vue，不被浏览器英文原生提示抢走。
