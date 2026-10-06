# JobProof AI P0A 数据权利前端交付说明

> 日期：2026-08-18
> 角色：P0A 数据权利前端官
> 编码：UTF-8
> 技术：Vue 3 + TypeScript + Vite。禁止用 Python 跑本项目。
> 性质：补 P0A-blocker「数据权利导出/删除/注销没有前端页」。接已有后端真实 API，无 Mock。未 git commit。未改 `backend/`。未改投递 / 面试 / 复盘 / 简历 / 岗位 / 匹配 / 工作台业务逻辑。

对照：`开发文档/JobProof-AI-P3.5-业务逻辑确认记录-v0.1.md` 12.4.15–18、12.1.4、13.2；`docs/plan/JobProof-AI-P0A-S0-后端交付说明.md`；`DataRightsController` 为 API 唯一真相。

## 1. 本刀交付了什么

路由 **`/account/data-rights`**（须登录）。顶栏、改密页、通知「导出/删除进度」、工作台 `ACCOUNT_EXPORT` / `ACCOUNT_DELETION` 均可进入此页，不再停在通知文案。

**未改 backend Java。** 下载若走 `GET /api/v1/data-rights/exports/{id}/download`，由 Vite `/api` 代理到 8080，Cookie `credentials: 'include'`。

### 已接操作（只消费已确认契约）

| 能力 | 方法 | 路径 | 页面表现 |
| --- | --- | --- | --- |
| 删除预览 | GET | `/api/v1/data-rights/deletions/preview` | 账号级缺省 `scope=ACCOUNT`：原文 + 空 `impacts` + `canProceed=true`。对象级 `scope=OBJECT&targetType&targetId`：照录 `impacts[]` / `canProceed` / `blockers`。账号预览与对象预览分区展示 |
| 提交删除 | POST | `/api/v1/data-rights/deletions` | 账号级 / 对象级分开，均二次确认 + `confirmationAck=true`。对象提交仅在预览 `scope=OBJECT` 且 `canProceed=true`、无 `BLOCKING` 时开放；进行中禁用重复提交 |
| 删除进度 | GET | `/api/v1/data-rights/deletions/{id}` | 状态与模块回执照录；`PARTIALLY_RESTRICTED` 不显示成全部完成 |
| 发起导出 | POST | `/api/v1/data-rights/exports` | 可选 `Idempotency-Key`；进行中禁用再发起 |
| 导出进度 | GET | `/api/v1/data-rights/exports/{id}`、`/api/v1/tasks/{id}` | 任务五态复用现有 `pollTask` / `fetchTask` |
| 取消 / 重试导出 | POST | `/exports/{id}/cancel`、`/retry` | 按任务可取消 / 可手动重试闸 |
| 限时下载 | GET | `/api/v1/data-rights/exports/{id}/download` | 仅当 `downloadAvailable=true`，或后端另给 `downloadUrl` / `fileId` |
| 文件限时地址 | GET | `/api/v1/files/{id}/download-url` | 仅当导出视图带 `fileId` 时才打 |

错误码走现有 `api()` / `apiDownload()`：401 清会话回登录，403 `ForbidState`，409 与其它 `reason`/`message` 原样展示。

`apiDownload` 不跟随重定向、拒绝 `text/html` 与空 body，Accept 允许 `application/json` / `octet-stream` / `pdf`。**不在共享层做 `%PDF-` 校验**（那只属于简历下载）。导出成功后的限时下载在 `dataRightsApi` 再验字节以 `{` / `[` 开头，拒绝 HTML 登录页和 PDF。

### 限时下载规则（禁止假下载）

当前 `ExportView` **没有** `fileId` / `downloadUrl`，只有 `downloadAvailable`、`downloadExpired`、`downloadExpiresAt`。因此：

1. 任务未成功 → 禁用下载并写明任务状态。
2. `downloadExpired=true` → 禁用，提示过期须重新导出。
3. `downloadAvailable=true` → 打真实 `GET .../exports/{id}/download`，用响应体触发浏览器下载。
4. 若后端以后补了 `downloadUrl` 或 `fileId`，按字段接；没有这些字段且 `downloadAvailable` 不为 true → **禁用下载**，文案写明原因，不把任务成功 JSON 当成文件。
5. 工作台若只给 `taskId`、没有 `exportId`：可看任务进度，不能下载。

## 2. 页面与入口

| 路由 / 入口 | 谁能进 | 做什么 |
| --- | --- | --- |
| `/account/data-rights` | 须登录 | 预览、导出、账号删除/注销、对象级入口、进度 |
| `/account` | 须登录 | 仍只做改密；增加「数据权利」链接 |
| 顶栏「数据权利」 | 已登录 | `AppChrome` 增加入口 |
| 工作台异步任务 | 已登录 | `ACCOUNT_EXPORT` → `?taskId=`；`ACCOUNT_DELETION` → `?taskId=&kind=deletion` |
| 通知 | 已登录 | `DATA_EXPORT_PROGRESS` → `?exportId=`（eventId）；`DATA_DELETION_PROGRESS` → `?deletionId=` |

未登录访问 → `/login?reason=unauthenticated&next=…`。

对象级：填 `targetType` / `targetId` 后刷新 **对象预览**（`scope=OBJECT`）。账号级预览与对象预览分区，互不覆盖。`canProceed=false` 或 `impacts` 含 `BLOCKING` 时禁用提交并展示 `blockers`。预览未返回 `scope=OBJECT` 时仍禁用，以免误走账号清理。TARGET/CASCADE 会物理删，BOUND/RELATED 只展示；本页不宣称「对象已单独删除成功」。

## 3. 怎么启动

两台进程，先后端后前端。不要用 Python。

```bash
cd backend
mvn spring-boot:run

cd frontend
npm install
npm run dev
```

浏览器：**http://localhost:5173/account/data-rights**（须先登录）。

```bash
cd frontend
npm run build
```

## 4. 状态覆盖

| 态 | 何处可看 |
| --- | --- |
| 正常 | 预览原文；导出成功后限时下载可用 |
| 加载 | 「正在读取…」；按钮 pending |
| 空 | 尚无导出/删除记录的斜体说明 |
| 错误 | 接口 `message`；后端未启动走既有网络文案 |
| 校验 | 导出/删除须勾选确认；删除第二次点击才提交 |
| 提交中 | 进行中禁用重复发起导出/删除 |
| 部分受限 | 明确不是全部完成 |
| 权限 | 401 回登录；403 `ForbidState` |
| 禁用下载 | 无 `fileId`/`downloadUrl` 且 `downloadAvailable` 不为 true |

## 5. 未做事项

- **对象级删除**：后端已交刀。本页已接 `scope=OBJECT` 预览/提交；不假装成功。分享创建/撤销、邮件短信、MFA、手机号、运营后台、P0B 仍未做。
- `GET /deletions` / `GET /exports` 列表（Controller 未暴露）
- 改 `backend/`；改 `features/application` `interview` `review` `resume` `job` `matching`；未改工作台页面业务逻辑（只改 `workspaceNav.ts` 的导出/删除 href）
- git commit；用 Python 跑项目

## 6. 目录

```txt
frontend/src/features/datarights/pages/DataRightsPage.vue
frontend/src/features/datarights/services/dataRightsApi.ts
frontend/src/features/datarights/types.ts
frontend/src/features/datarights/labels.ts
frontend/src/features/datarights/index.ts
frontend/src/app/router.ts
frontend/src/shared/lib/workspaceNav.ts
frontend/src/shared/ui/AppChrome.vue
frontend/src/shared/api/client.ts          新增 apiDownload（401/403/409 同封装）
frontend/src/features/identity/pages/AccountPage.vue
frontend/src/features/notification/pages/NotificationListPage.vue
frontend/src/features/notification/labels.ts
frontend/src/style.css
```

## 7. 回滚

删除 `frontend/src/features/datarights/`，还原上表其余文件中本刀改动即可。勿用本页字段倒逼改冻结契约。对象级删除以后端官为准。
