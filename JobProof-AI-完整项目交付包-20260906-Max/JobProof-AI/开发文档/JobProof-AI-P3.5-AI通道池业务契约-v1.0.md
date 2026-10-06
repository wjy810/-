# JobProof AI P3.5 AI 通道池业务契约

> 文档版本：v1.0  
> 冻结日期：2026-08-19  
> 状态：已冻结，可进入数据库、后端与前端实现  
> 范围：系统通道、个人通道、模型目录、模型定价、钱包、订阅、用量、账单、健康与审计

## 1. 业务方向与边界

采用「完整版通道池」：管理员提供系统通道，用户按实际用量以美元余额结算；用户可维护个人通道，但只有个人通道月付订阅有效时可调用。系统通道和个人通道不得混用计费模式。

一期不做充值支付网关、欠费授信、组织钱包、跨币种、优惠券、平台代理加价、个人密钥共享和用户自定义路由算法。个人通道订阅价格由服务端配置，首期为每自然月 USD 9.90；首次开通与续费均从钱包扣款，默认自动续费，用户可关闭自动续费；关闭只影响下周期，当前周期不退款。

## 2. 业务对象

| 对象 | 所有者 | 说明 |
| --- | --- | --- |
| AI 通道 | 管理员或用户 | `SYSTEM` 为平台系统通道；`PERSONAL` 为用户个人通道；密钥只保存加密密文和掩码 |
| AI 模型 | 平台 | 规范模型目录，区分 `TOKEN` 与 `FIXED` 计价 |
| 通道模型 | 通道所有者/管理员 | 绑定通道可用模型，可独立启用、隐藏、排序 |
| 模型价格 | 管理员 | 版本化生效；新请求快照，历史账单不追溯重算 |
| 钱包账户/流水/冻结 | 用户 | 余额币种固定 USD；冻结、补扣、释放、退款必须幂等 |
| 个人通道订阅 | 用户 | `ACTIVE` 时个人通道才可调用 |
| 用量/账单 | 系统 | 记录调用、Token/成功结果和不可变价格快照 |
| 通道健康 | 系统 | 最近探测状态、延迟和失败次数，不包含密钥与响应正文 |
| AI 审计日志 | 系统 | 记录敏感配置和资金相关操作，不记录密钥明文、提示词或模型正文 |

## 3. 冻结计费规则

1. 钱包和价格金额统一 USD，数据库金额 `DECIMAL(19,8)`，Java 使用 `BigDecimal`；展示可按产品需要舍入，但结算不使用浮点数。
2. 文本模型价格参数顺序固定为：模型固定价格、模型倍率、提示缓存倍率、模型补全倍率。
3. 文本费用：
   - 普通输入 = `inputTokens / 1,000,000 × modelRate × channelRate`
   - 缓存输入 = `cachedInputTokens / 1,000,000 × modelRate × cacheRate × channelRate`
   - 输出 = `outputTokens / 1,000,000 × modelRate × completionRate × channelRate`
   - 最终费用为三项之和，按 8 位小数 `HALF_UP`。
4. 图片/视频等固定价格模型：`fixedPrice × successfulUnits × channelRate`；只有成功结果计费，多项成功结果逐项累加。
5. 媒体预估使用请求的 `requestedUnits`；文本预估使用客户端允许的最大输入与最大输出 Token，缓存命中按 0 估算。不得以平均历史用量降低冻结额。
6. 调用前冻结预估最高费用；完成后据实结算，多退少补。补扣失败时停止继续生成，账单为 `PAYMENT_REQUIRED`，钱包不得变为负数。
7. 供应商在任何可计费用量产生前失败：最终费用 0、全额释放。已返回可验证用量或成功媒体结果后失败：仅按实际成功部分结算。超时且供应商用量未知：不扣款、全额释放并标记 `USAGE_UNKNOWN`，不得猜测计费。
8. 已结算账单只允许管理员发起全额或部分退款；退款金额不得超过「已扣金额 - 已退款金额」，原账单不删除，新增 `REFUND` 钱包流水和退款后状态。
9. 价格、倍率和通道绑定变化只影响新请求；用量和账单必须固化模型、通道、价格版本、四项价格参数及通道倍率快照。
10. 个人通道调用不按 Token 扣款；其供应商费用由用户自行承担，平台只校验订阅与通道归属。

## 4. CRUD 操作矩阵

| 对象 | 求职者本人 | 管理员 | 删除/限制 |
| --- | --- | --- | --- |
| 系统通道 | 查看可用通道脱敏信息 | 新增、编辑、启停、检测、绑定模型 | 不物理删除；停用保留历史 |
| 个人通道 | 新增、查看本人、编辑、启停、检测 | 只看脱敏元数据与审计 | 归档；密钥密文同步失效 |
| 模型目录 | 查看启用模型 | 新增、编辑、启停 | 停用不改历史调用 |
| 通道模型 | 查看本人可用项 | 绑定、启停、隐藏、排序、刷新 | 解除绑定不得影响历史快照 |
| 模型价格 | 查看当前有效价格 | 新增价格版本、启停 | 已生效版本不可原地改 |
| 钱包 | 查看余额、流水、冻结、账单 | 调整余额、退款并审计 | 不允许物理删除资金记录 |
| 订阅 | 开通、关自动续费、查看 | 查看、按审计流程终止 | 当前周期不退款 |
| 用量/账单 | 只看本人 | 脱敏查询、退款 | 不可编辑或删除 |

## 5. 页面—业务操作映射

| 页面 | 已冻结操作 | 禁止默认增加 |
| --- | --- | --- |
| AI 配置/通道池 | 查看系统/个人通道、筛选分页、添加个人通道、编辑、启停、检测、归档 | 展示密钥明文、普通用户改系统通道 |
| 模型与价格 | 模型列表、通道绑定、启停/隐藏/排序、价格版本 | 覆盖历史价格、用户改系统价格 |
| 钱包与账单 | 余额、冻结、流水、用量和账单分页 | 负余额、删除流水、前端自行算最终账单 |
| 个人通道订阅 | 开通、关闭自动续费、周期/状态展示 | 未订阅试用个人通道、周期中途退款 |
| 管理后台 | 系统通道、模型、价格、健康、脱敏账单、退款、审计 | 查看个人密钥明文、查看提示词/模型正文 |

## 6. 权限矩阵

| 操作 | USER | ADMIN |
| --- | --- | --- |
| 查看可用系统通道/模型/有效价格 | ✓ | ✓ |
| 维护本人个人通道/订阅/钱包查询 | ✓ | 仅脱敏审计查询 |
| 维护系统通道、模型、绑定和价格 | — | ✓ |
| 钱包人工调整、账单退款 | — | ✓，必须审计与幂等 |
| 查看密钥明文 | — | —；服务端仅调用时解密 |

管理员角色字段沿用 `accounts.role`，管理员值为 `ADMIN`；普通值为 `USER`。所有对象级查询同时校验 `account_id` 归属，不得仅依赖前端隐藏。

## 7. 状态流转

- 通道：`DRAFT -> ACTIVE <-> DISABLED -> ARCHIVED`；检测结果不直接改变业务状态。
- 通道模型：`ACTIVE <-> DISABLED`，另有 `HIDDEN`；通道停用时绑定项不可路由，但不批量改历史状态。
- 模型/价格：`ACTIVE <-> DISABLED`；新价格创建新版本，旧版本保留。
- 钱包冻结：`HELD -> SETTLED | RELEASED | EXPIRED`；终态不可再次结算。
- 用量：`PENDING -> RUNNING -> SUCCEEDED | FAILED | CANCELLED`。
- 账单：`PENDING -> SETTLED | PAYMENT_REQUIRED | VOID`；`SETTLED -> PARTIALLY_REFUNDED -> REFUNDED`。
- 订阅：`ACTIVE -> CANCEL_AT_PERIOD_END -> EXPIRED`；续费成功回到 `ACTIVE` 并推进周期，续费余额不足进入 `PAST_DUE`，个人通道立即不可调用；补款成功可恢复。
- 健康：`UNKNOWN -> HEALTHY | DEGRADED | UNHEALTHY`。

## 8. 模块交互与事务边界

| 发起 | 接收 | 交互 | 一致性/补偿 |
| --- | --- | --- | --- |
| AI Gateway | AI Config | 选择启用模型与健康通道，读取密钥 | 只读；不得返回密钥给 Web |
| AI Gateway | Billing | 调用前预授权、完成后结算 | 冻结/账单/流水单事务；供应商调用在事务外 |
| Billing | Wallet | 冻结、扣款、释放、退款 | 乐观锁 `versionNo` + 幂等键 |
| Billing | Subscription | 个人通道调用前校验 | 非 `ACTIVE`/有效周期拒绝 |
| Health | AI Config | 探测并写健康快照 | 探测失败不修改用户配置 |
| Admin | Audit | 敏感配置、调整、退款 | 业务提交与审计同事务或外盒补偿 |

## 9. HTTP 契约

统一前缀 `/api/v1/ai`；JSON 字段使用 camelCase；时间为 UTC ISO-8601；列表统一返回 `PageResponse<T>`：`{items,page,size,totalElements,totalPages}`，`page` 从 0 开始，`size` 默认 20、最大 100。写请求的 `idempotencyKey` 最大 128；更新请求必须携带 `versionNo`，成功响应返回新 `versionNo`。

### 9.1 用户端端点

| Method | Path | Request DTO | Response DTO |
| --- | --- | --- | --- |
| GET | `/channels?scope=SYSTEM|PERSONAL&status=&page=&size=` | — | `PageResponse<AiChannelResponse>` |
| POST | `/channels/personal` | `CreatePersonalChannelRequest` | `AiChannelResponse` |
| PUT | `/channels/personal/{channelId}` | `UpdatePersonalChannelRequest` | `AiChannelResponse` |
| POST | `/channels/personal/{channelId}/test` | `TestChannelRequest` | `ChannelTestResponse` |
| POST | `/channels/personal/{channelId}/archive` | `VersionedCommandRequest` | `AiChannelResponse` |
| GET | `/models?channelId=&status=&page=&size=` | — | `PageResponse<AiModelResponse>` |
| GET | `/pricing?modelId=&page=&size=` | — | `PageResponse<AiModelPricingResponse>` |
| GET | `/wallet` | — | `WalletResponse` |
| GET | `/wallet/ledger?type=&page=&size=` | — | `PageResponse<WalletLedgerResponse>` |
| GET | `/usage?status=&from=&to=&page=&size=` | — | `PageResponse<AiUsageResponse>` |
| GET | `/billing?status=&from=&to=&page=&size=` | — | `PageResponse<AiBillingResponse>` |
| GET | `/subscriptions/personal-channel` | — | `PersonalChannelSubscriptionResponse` |
| POST | `/subscriptions/personal-channel` | `SubscribePersonalChannelRequest` | `PersonalChannelSubscriptionResponse` |
| POST | `/subscriptions/personal-channel/cancel-renewal` | `VersionedCommandRequest` | `PersonalChannelSubscriptionResponse` |

### 9.2 管理端端点

| Method | Path | Request DTO | Response DTO |
| --- | --- | --- | --- |
| POST | `/admin/channels` | `CreateSystemChannelRequest` | `AiChannelResponse` |
| PUT | `/admin/channels/{channelId}` | `UpdateSystemChannelRequest` | `AiChannelResponse` |
| POST | `/admin/channels/{channelId}/test` | `TestChannelRequest` | `ChannelTestResponse` |
| POST | `/admin/channels/{channelId}/models/refresh` | `VersionedCommandRequest` | `ChannelModelRefreshResponse` |
| PUT | `/admin/channels/{channelId}/models/{modelId}` | `UpdateChannelModelRequest` | `AiChannelModelResponse` |
| POST | `/admin/models` | `CreateAiModelRequest` | `AiModelResponse` |
| PUT | `/admin/models/{modelId}` | `UpdateAiModelRequest` | `AiModelResponse` |
| POST | `/admin/models/{modelId}/pricing` | `CreateModelPricingRequest` | `AiModelPricingResponse` |
| POST | `/admin/wallets/{accountId}/adjustments` | `WalletAdjustmentRequest` | `WalletLedgerResponse` |
| POST | `/admin/billing/{billingId}/refunds` | `RefundBillingRequest` | `AiBillingResponse` |
| GET | `/admin/health?status=&page=&size=` | — | `PageResponse<AiChannelHealthResponse>` |
| GET | `/admin/audit?action=&objectType=&page=&size=` | — | `PageResponse<AiAuditResponse>` |

### 9.3 DTO 字段

- `CreatePersonalChannelRequest`: `name,providerCode,baseUrl,apiKey,protocol,idempotencyKey`。
- `UpdatePersonalChannelRequest`: `name,baseUrl,apiKey?,status,versionNo,idempotencyKey`；`apiKey` 缺省表示不变，禁止回传掩码充当新密钥。
- `CreateSystemChannelRequest`: `name,providerCode,baseUrl,apiKey,protocol,channelRate,status,idempotencyKey`。
- `UpdateSystemChannelRequest`: `name,baseUrl,apiKey?,protocol,channelRate,status,versionNo,idempotencyKey`。
- `AiChannelResponse`: `id,scope,ownerAccountId?,name,providerCode,baseUrl,protocol,apiKeyMasked,status,channelRate,healthStatus,lastCheckedAt,versionNo,createdAt,updatedAt`。
- `CreateAiModelRequest`: `modelCode,displayName,providerCode,modelType,billingMode,unit,idempotencyKey`。
- `UpdateAiModelRequest`: `displayName,status,versionNo,idempotencyKey`。
- `AiModelResponse`: `id,modelCode,displayName,providerCode,modelType,billingMode,unit,status,versionNo`。
- `UpdateChannelModelRequest`: `providerModelCode,status,hidden,priority,versionNo,idempotencyKey`。
- `AiChannelModelResponse`: `id,channelId,modelId,providerModelCode,status,hidden,priority,versionNo`。
- `CreateModelPricingRequest`: `fixedPrice,modelRate,cacheRate,completionRate,currency,effectiveFrom,idempotencyKey`。
- `AiModelPricingResponse`: `id,modelId,fixedPrice,modelRate,cacheRate,completionRate,currency,status,effectiveFrom,effectiveTo,versionNo`。
- `WalletResponse`: `accountId,currency,availableBalance,heldBalance,versionNo,updatedAt`。
- `WalletLedgerResponse`: `id,type,amount,balanceAfter,referenceType,referenceId,idempotencyKey,createdAt`。
- `WalletAdjustmentRequest`: `amount,reason,idempotencyKey,versionNo`；正数入账、负数扣减，扣减不得造成负可用余额。
- `AiUsageResponse`: `id,requestId,channelId,modelId,status,inputTokens,cachedInputTokens,outputTokens,successfulUnits,failureCode,startedAt,completedAt`。
- `AiBillingResponse`: `id,usageId,status,currency,estimatedAmount,actualAmount,refundedAmount,pricingSnapshot,channelRateSnapshot,settledAt,versionNo`。
- `SubscribePersonalChannelRequest`: `autoRenew,idempotencyKey,versionNo`。
- `PersonalChannelSubscriptionResponse`: `id,status,price,currency,currentPeriodStart,currentPeriodEnd,autoRenew,versionNo`。
- `RefundBillingRequest`: `amount,reason,idempotencyKey,versionNo`。
- `VersionedCommandRequest`: `versionNo,idempotencyKey`。
- `TestChannelRequest`: `idempotencyKey`。
- `ChannelTestResponse`: `channelId,status,latencyMs,checkedAt,failureCode?,message?`；不得含供应商原始敏感响应。

## 10. 错误码

| HTTP | code | 语义 |
| --- | --- | --- |
| 400 | `AI_VALIDATION_FAILED` | 字段、URL、金额、价格或状态参数无效 |
| 401 | `UNAUTHORIZED` | 未登录 |
| 403 | `AI_CHANNEL_FORBIDDEN` | 非本人个人通道或非管理员操作 |
| 404 | `AI_CHANNEL_NOT_FOUND` / `AI_MODEL_NOT_FOUND` / `AI_BILLING_NOT_FOUND` | 对象不存在或不可见 |
| 409 | `AI_VERSION_CONFLICT` | `versionNo` 过期 |
| 409 | `AI_IDEMPOTENCY_CONFLICT` | 相同幂等键对应不同请求 |
| 409 | `AI_STATE_CONFLICT` | 当前状态不允许操作 |
| 402 | `AI_INSUFFICIENT_BALANCE` | 无法冻结或补扣 |
| 402 | `AI_SUBSCRIPTION_REQUIRED` | 个人通道订阅无效 |
| 422 | `AI_PRICING_NOT_CONFIGURED` | 系统模型无有效价格 |
| 422 | `AI_MODEL_NOT_AVAILABLE` | 模型未绑定、隐藏、停用或无健康通道 |
| 502 | `AI_PROVIDER_FAILED` | 供应商失败，已按实际用量结算/释放 |
| 504 | `AI_PROVIDER_TIMEOUT` | 供应商超时 |
| 500 | `SYSTEM_FAILURE` | 非预期故障，不暴露密钥与供应商正文 |

## 11. 幂等、并发与安全

- 创建、冻结、结算、退款、订阅、续费、钱包调整和通道检测均要求幂等键；同一账户、操作类型、幂等键唯一。
- `wallet_account`、`wallet_hold`、订阅、通道、通道模型、模型、价格和账单使用 `version_no` 乐观锁；冲突拒绝覆盖。
- API Key 写入后只返回尾四位掩码；密文使用应用级主密钥加密，日志、异常、审计和 DTO 不得出现明文。
- `baseUrl` 只允许 HTTPS（本地开发显式白名单除外），解析和重定向前后阻断回环、私网、链路本地和云元数据地址。
- 审计只记录 actor、动作、对象、结果、差异摘要与追踪号，不保存 API Key、提示词、模型响应或钱包完整请求正文。

## 12. 验收场景

1. 系统通道余额不足在供应商调用前返回 402，钱包无流水外副作用。
2. 成功调用释放多余冻结并生成唯一账单；重复回调/结算不重复扣款。
3. 用量未知超时全额释放；有成功媒体结果的部分失败只收成功项。
4. 价格变更后旧账单快照不变，新请求命中新版本。
5. 无有效订阅的个人通道返回 402；关闭自动续费后当前周期仍可用。
6. 普通用户不能修改系统通道/价格，管理员也不能读取个人 API Key 明文。
7. 旧 `versionNo` 返回 409；同幂等键不同请求返回幂等冲突。
8. 分页 `size>100` 返回校验错误，不静默拉取全量。
