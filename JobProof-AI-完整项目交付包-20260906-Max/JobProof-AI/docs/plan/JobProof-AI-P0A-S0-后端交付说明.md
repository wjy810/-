# JobProof AI P0A S0 后端交付说明

> 日期：2026-08-18
> 角色：S0 后端切片官（底座使）
> 编码：UTF-8
> 性质：本刀可运行 API + 契约说明 + 验收门对照。**不是**「S0 前端可开工」宣布；调度官仍需走交接仪式。

## 1. 本切片交付了什么

可运行后端位于 `backend/`：Java 17 + Spring Boot 3 模块化单体（包边界按 Identity / Data Rights / Task / Notification / Audit / Storage 划分）。独立 Worker 有主类骨架，S0 默认 **in-process** 跑在同一应用里。

### 已实现命令 / 查询

| 能力 | 方法 | 路径 | 权限 |
| --- | --- | --- | --- |
| 注册 | POST | `/api/v1/auth/register` | 匿名 |
| 登录（HttpOnly Cookie `jobproof_session`） | POST | `/api/v1/auth/login` | 匿名 |
| 退出当前会话 | POST | `/api/v1/auth/logout` | 本人会话 |
| 登录态改密（随后全部会话失效） | POST | `/api/v1/auth/password/change` | 本人 |
| 申请重置验证码 | POST | `/api/v1/auth/password/reset/request` | 匿名；不暴露邮箱是否存在 |
| 验证码确认改密（失败则拒绝；成功后全部会话失效） | POST | `/api/v1/auth/password/reset/confirm` | 匿名 |
| 当前用户 | GET | `/api/v1/me` | 本人 |
| 删除预览（关联影响 / 不可逆 / 法定例外入口） | GET | `/api/v1/data-rights/deletions/preview` | 本人 |
| 提交删除申请 | POST | `/api/v1/data-rights/deletions` | 本人；须 `confirmationAck=true` |
| 查询删除进度与模块回执 | GET | `/api/v1/data-rights/deletions/{id}` | 对象级本人 |
| 发起账号级导出 | POST | `/api/v1/data-rights/exports` | 本人；`Idempotency-Key` 可选 |
| 查询导出 / 任务 | GET | `/api/v1/data-rights/exports/{id}`、`/api/v1/tasks/{id}` | 对象级本人 |
| 取消 / 手动重试导出 | POST | `/.../exports/{id}/cancel`、`/retry` | 按任务五态契约 |
| 限时下载 | GET | `/api/v1/data-rights/exports/{id}/download` | 成功且未过期 |
| 通知列表 / 已读 | GET/POST | `/api/v1/notifications` | 本人 |
| 只读限时分享与撤销 | POST | `/api/v1/data-rights/shares`、`/shares/{id}/revoke` | 本人 |
| 私有文件 Spike | POST/GET | `/api/v1/files`、`/files/{id}/download-url` | 对象级授权；运营不能看他人原文 |

错误类别：`USER_CORRECTABLE` / `FORBIDDEN` / `CONFLICT` / `DEPENDENCY_FAILED` / `SYSTEM_FAILURE` / `REQUIRES_HUMAN` / `UNAUTHENTICATED`。不回传供应商堆栈。

### 状态（未发明新名）

- 删除请求：`SUBMITTED` / `PROCESSING` / `PARTIALLY_RESTRICTED` / `COMPLETED` / `FAILED`（对应已提交、处理中、部分受限、已完成、失败）
- 长任务：`PENDING` / `RUNNING` / `SUCCEEDED` / `FAILED` / `CANCELLED`
- 通知：写入成功即 `DELIVERED`，可 `READ`；另有失败/过期/归档枚举

S0 删除编排在审计索引保留时会落到 **部分受限**，提交当时一定是 **已提交**，不会假装全库已删完。画像 / 证据 / 岗位 / 匹配已由各模块 `DeletionModuleHandler` 回执清理（处理器未注册时才 `SKIPPED`）。`DataRightsAndNotificationIT` 已核 job/matching 回执为 `SUCCEEDED`。

### 事件通道（本刀接通）

外盒表 `outbox_events`：`AuthorizationRevoked`、`DeletionRequested`、`TaskCompleted`、`TaskFailed`、`NotificationRequested` 相关消费。通知失败不回滚业务；同一 `接收人 + eventId + 类型` 不去第二张有效通知。

## 2. 怎么启动

见 `backend/README.md`。最短路径：

```bash
cd backend
mvn test
mvn spring-boot:run
```

无 MySQL/Redis 用 `dev`（H2）。有 Docker 时 `docker compose up -d` 后 `mvn spring-boot:run -Dspring-boot.run.profiles=local`。

## 3. 验收门对照

| 门 | 结果 |
| --- | --- |
| MOCK-AUTH-001 邮箱登录/改密，改密后旧会话失效 | 已用 `AuthFlowIT` 演示 |
| 邮箱验证码重置；错误验证码拒绝 | 已用 `AuthFlowIT` 演示 |
| MOCK-DATA-002 删除部分失败/受限时进度可见，不得提前显示完成 | `DataRightsAndNotificationIT`：提交为 SUBMITTED，编排后为 PARTIALLY_RESTRICTED |
| MOCK-NOTIFY-001 同一事件不生成第二条 | 领域服务去重 + IT |
| MOCK-NOTIFY-002 通知失败业务状态不变 | 创建导出时模拟通知失败，任务仍可 SUCCEEDED |
| 对象级授权 | 他人导出 403 |
| 运营默认不看原文 | 审计摘要脱敏；运营角色禁止下载体文件 |
| 架构 19.2 私有文件 Spike | 本地存储 + 10 分钟签名 URL |
| PRD 23.1 画像/JD/简历/投递 | **未做（禁止本刀偷做 S1–S4）** |

## 4. 前端还不能接哪些

调度官未宣布「S0 后端可交接」前，前端按约定按兵不动。即便消费本契约，也 **不能** 接：

- 画像正式事实 / 候选 / 完整度
- 六类证据、文件作为证据引用
- JD 导入、岗位版本、匹配报告
- 简历主档/冻结、投递 CRM
- 面试轮次、复盘
- 工作台卡片、排序、快捷操作
- 邮件/短信、手机号/第三方/MFA、运营可视化后台

S0 登录成功后没有求职工作台业务对象可展示。导出 JSON 仅为账号级占位，明确列出尚未接入的模块。

## 5. 未做事项

- 未接真实 SMTP；验证码只进开发邮箱接口
- 未接真实 S3；本地目录模拟短时签名
- Redis 仅配置位，S0 默认关闭以免无 Redis 无法启动
- 未写 Vue、未开 S1、未 commit
- 完整 CSRF 双提交、生产密钥托管、运营工单后台均未做

## 6. 本轮硬化（迭代官，2026-08-18）

- 通知渠道失败改为独立事务记录 `SEND_FAILED`，**不再向外抛异常**，避免把导出 / 删除等业务事务标成 rollback-only（MOCK-NOTIFY-002）。
- 登录 Cookie 有效期与会话 `expiresAt` 对齐，不再硬编码 14 天。
- 增补：邮箱 / 弱密码错误码、改密后**全部**会话失效、通知失败后导出仍可成功。
- 本刀仍未宣布「S0 前端可开工」；未开 S1。

## 7. 对象级删除（P0A-gap，2026-08-18）

`GET /api/v1/data-rights/deletions/preview` 现接受 `scope` / `targetType` / `targetId`（缺省仍为账号级）。`OBJECT` 按目标对象做影响分析与物理编排，**不再走账号级清理**；预览返回投递 / 冻结版本 / 面试 / 复盘等真实关联清单。账号级删除 / 注销 / 导出语义不变。物理删除仍只走数据权利流程。
