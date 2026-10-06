package com.jobproof.modules.aibilling.application;

import com.jobproof.modules.aibilling.infra.*;
import com.jobproof.shared.auth.CurrentAccount;
import com.jobproof.shared.error.AppException;
import java.math.BigDecimal;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiUserBillingQueryService {
    private final WalletAccountJpaRepository wallets;
    private final WalletLedgerJpaRepository ledgers;
    private final AiUsageRecordJpaRepository usages;
    private final AiBillingRecordJpaRepository bills;
    private final PersonalChannelSubscriptionJpaRepository subscriptions;

    public AiUserBillingQueryService(WalletAccountJpaRepository wallets, WalletLedgerJpaRepository ledgers,
            AiUsageRecordJpaRepository usages, AiBillingRecordJpaRepository bills,
            PersonalChannelSubscriptionJpaRepository subscriptions) {
        this.wallets = wallets;
        this.ledgers = ledgers;
        this.usages = usages;
        this.bills = bills;
        this.subscriptions = subscriptions;
    }

    @Transactional(readOnly = true)
    public WalletView wallet(CurrentAccount current) {
        assertAllowed(current);
        WalletAccountEntity entity = wallets.findByAccountIdAndCurrency(current.accountId(), "USD")
                .orElseThrow(() -> AppException.user("AI_WALLET_NOT_FOUND", "钱包不存在"));
        return new WalletView(current.accountId(), entity.getCurrency(), entity.getAvailableBalance(),
                entity.getHeldBalance(), entity.getVersionNo(), entity.getUpdatedAt());
    }

    @Transactional(readOnly = true)
    public Page<LedgerView> ledger(CurrentAccount current, String type, Pageable pageable) {
        assertAllowed(current);
        Page<WalletLedgerEntity> result = type == null || type.isBlank()
                ? ledgers.findByAccountId(current.accountId(), pageable)
                : ledgers.findByAccountIdAndLedgerType(current.accountId(), type, pageable);
        return result.map(entity -> new LedgerView(entity.getId(), entity.getLedgerType(), entity.getAmount(),
                entity.getBalanceAfter(), entity.getReferenceType(), entity.getReferenceId(),
                entity.getIdempotencyKey(), entity.getCreatedAt()));
    }

    @Transactional(readOnly = true)
    public Page<UsageView> usage(CurrentAccount current, Pageable pageable) {
        assertAllowed(current);
        return usages.findByAccountId(current.accountId(), pageable).map(entity -> new UsageView(
                entity.getId(), entity.getRequestId(), entity.getChannelId(), entity.getModelId(), entity.getStatus(),
                entity.getInputTokens(), entity.getCachedInputTokens(), entity.getOutputTokens(),
                entity.getSuccessfulUnits(), entity.getFailureCode(), entity.getStartedAt(), entity.getCompletedAt()));
    }

    @Transactional(readOnly = true)
    public Page<BillingView> billing(CurrentAccount current, Pageable pageable) {
        assertAllowed(current);
        return bills.findByAccountId(current.accountId(), pageable).map(entity -> new BillingView(
                entity.getId(), entity.getUsageId(), entity.getStatus(), entity.getCurrency(),
                entity.getEstimatedAmount(), entity.getActualAmount(), entity.getRefundedAmount(),
                entity.getPricingSnapshot(), entity.getChannelRateSnapshot(), entity.getSettledAt(),
                entity.getVersionNo()));
    }

    @Transactional(readOnly = true)
    public SubscriptionView subscription(CurrentAccount current) {
        assertAllowed(current);
        PersonalChannelSubscriptionEntity entity = subscriptions.findByAccountId(current.accountId())
                .orElseThrow(() -> AppException.user("AI_SUBSCRIPTION_NOT_FOUND", "个人通道订阅不存在"));
        return new SubscriptionView(entity.getId(), entity.getStatus(), entity.getPrice(), entity.getCurrency(),
                entity.getCurrentPeriodStart(), entity.getCurrentPeriodEnd(), entity.isAutoRenew(), entity.getVersionNo());
    }

    public SubscriptionView unsupportedSubscriptionWrite(CurrentAccount current) {
        assertAllowed(current);
        throw AppException.conflict("AI_STATE_CONFLICT",
                "V7 缺少安全复用订阅写服务所需的完整状态列，订阅写操作暂不可用");
    }

    private static void assertAllowed(CurrentAccount current) {
        if (!"USER".equals(current.role()) && !"ADMIN".equals(current.role())) {
            throw AppException.forbidden("AI_CHANNEL_FORBIDDEN", "仅 USER/ADMIN 可访问 AI 通道池");
        }
    }

    public record WalletView(String accountId, String currency, BigDecimal availableBalance,
            BigDecimal heldBalance, int versionNo, Instant updatedAt) {}
    public record LedgerView(String id, String type, BigDecimal amount, BigDecimal balanceAfter,
            String referenceType, String referenceId, String idempotencyKey, Instant createdAt) {}
    public record UsageView(String id, String requestId, String channelId, String modelId, String status,
            long inputTokens, long cachedInputTokens, long outputTokens, int successfulUnits, String failureCode,
            Instant startedAt, Instant completedAt) {}
    public record BillingView(String id, String usageId, String status, String currency, BigDecimal estimatedAmount,
            BigDecimal actualAmount, BigDecimal refundedAmount, String pricingSnapshot,
            BigDecimal channelRateSnapshot, Instant settledAt, int versionNo) {}
    public record SubscriptionView(String id, String status, BigDecimal price, String currency,
            Instant currentPeriodStart, Instant currentPeriodEnd, boolean autoRenew, int versionNo) {}
}
