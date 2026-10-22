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
    private final BalanceWalletService balances;
    private final WalletAccountJpaRepository wallets;
    private final WalletLedgerJpaRepository ledgers;
    private final AiUsageRecordJpaRepository usages;
    private final AiBillingRecordJpaRepository bills;
    private final PersonalChannelSubscriptionJpaRepository subscriptions;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    private final com.jobproof.shared.time.ClockPort clock;

    public AiUserBillingQueryService(BalanceWalletService balances, WalletAccountJpaRepository wallets, WalletLedgerJpaRepository ledgers,
            AiUsageRecordJpaRepository usages, AiBillingRecordJpaRepository bills,
            PersonalChannelSubscriptionJpaRepository subscriptions, org.springframework.jdbc.core.JdbcTemplate jdbc,
            com.jobproof.shared.time.ClockPort clock) {
        this.balances=balances;
        this.wallets = wallets;
        this.ledgers = ledgers;
        this.usages = usages;
        this.bills = bills;
        this.subscriptions = subscriptions;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional
    public WalletView wallet(CurrentAccount current) {
        assertAllowed(current);
        var entity=balances.wallet(current.accountId());
        return new WalletView(current.accountId(),entity.currency(),entity.availableBalance(),entity.heldBalance(),entity.versionNo(),entity.updatedAt());
    }

    @Transactional(readOnly = true)
    public Page<LedgerView> ledger(CurrentAccount current, String type, Pageable pageable) {
        assertAllowed(current);
        Page<WalletLedgerEntity> result=ledgers.findCny(current.accountId(),type==null||type.isBlank()?null:type,pageable);
        return result.map(entity -> new LedgerView(entity.getId(), entity.getLedgerType(), entity.getAmount(),
                entity.getBalanceAfter(), entity.getReferenceType(), entity.getReferenceId(),
                entity.getIdempotencyKey(), entity.getCreatedAt()));
    }

    @Transactional(readOnly = true)
    public Page<UsageView> usage(CurrentAccount current, Pageable pageable) {
        return usage(current, null, null, null, pageable);
    }
    @Transactional(readOnly = true)
    public Page<UsageView> usage(CurrentAccount current, String status, String from, String to, Pageable pageable) {
        assertAllowed(current);
        return usages.findAll(filter(current.accountId(), status, from, to, "startedAt"), pageable).map(entity -> new UsageView(
                entity.getId(), entity.getRequestId(), entity.getChannelId(), entity.getModelId(), entity.getStatus(),
                entity.getInputTokens(), entity.getCachedInputTokens(), entity.getOutputTokens(),
                entity.getSuccessfulUnits(), entity.getFailureCode(), entity.getStartedAt(), entity.getCompletedAt()));
    }

    @Transactional(readOnly = true)
    public Page<BillingView> billing(CurrentAccount current, Pageable pageable) {
        return billing(current, null, null, null, pageable);
    }
    @Transactional(readOnly = true)
    public Page<BillingView> billing(CurrentAccount current, String status, String from, String to, Pageable pageable) {
        assertAllowed(current);
        return bills.findAll(filter(current.accountId(), status, from, to, "createdAt"), pageable).map(entity -> new BillingView(
                entity.getId(), entity.getUsageId(), entity.getStatus(), entity.getCurrency(),
                entity.getEstimatedAmount(), entity.getActualAmount(), entity.getRefundedAmount(),
                entity.getPricingSnapshot(), entity.getChannelRateSnapshot(), entity.getSettledAt(),
                entity.getVersionNo()));
    }

    @Transactional(readOnly = true)
    public SubscriptionView subscription(CurrentAccount current) {
        assertAllowed(current);
        PersonalChannelSubscriptionEntity entity = subscriptions.findByAccountId(current.accountId())
                .orElse(null);
        if(entity==null)return new SubscriptionView(null,"INACTIVE",BigDecimal.ZERO,"CNY",null,null,false,0);
        return new SubscriptionView(entity.getId(), "ACTIVE".equals(entity.getStatus()) && !entity.getCurrentPeriodEnd().isAfter(clock.now()) ? "EXPIRED" : entity.getStatus(), entity.getPrice(), entity.getCurrency(),
                entity.getCurrentPeriodStart(), entity.getCurrentPeriodEnd(), false, entity.getVersionNo());
    }

    @Transactional
    public SubscriptionView cancelRenewal(CurrentAccount current, int expectedVersion) {
        assertAllowed(current);
        balances.lock(current.accountId());
        var value = subscription(current);
        if (value.id() == null) return value;
        if (jdbc.update("UPDATE personal_channel_subscription SET auto_renew=FALSE,version_no=version_no+1,updated_at=? WHERE account_id=? AND version_no=?", clock.now(), current.accountId(), expectedVersion) != 1)
            throw AppException.conflict("AI_STATE_CONFLICT", "订阅状态已更新，请刷新");
        return new SubscriptionView(value.id(), value.status(), value.price(), value.currency(), value.currentPeriodStart(), value.currentPeriodEnd(), false, expectedVersion + 1);
    }

    private static <T> org.springframework.data.jpa.domain.Specification<T> filter(String account, String status, String from, String to, String timeField) {
        Instant start = parseDate(from); Instant end = parseDate(to);
        if (start != null && end != null && !start.isBefore(end)) throw AppException.user("DATE_RANGE_INVALID", "结束时间需要晚于开始时间");
        return (root, query, builder) -> {
            var items = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            items.add(builder.equal(root.get("accountId"), account));
            if (status != null && !status.isBlank()) items.add(builder.equal(root.get("status"), status));
            if (start != null) items.add(builder.greaterThanOrEqualTo(root.get(timeField), start));
            if (end != null) items.add(builder.lessThan(root.get(timeField), end));
            return builder.and(items.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }
    private static Instant parseDate(String text) {
        if (text == null || text.isBlank()) return null;
        try { return Instant.parse(text); }
        catch (java.time.format.DateTimeParseException e) { throw AppException.user("DATE_RANGE_INVALID", "请使用有效时间"); }
    }

    private static void assertAllowed(CurrentAccount current) {
        if (!"USER".equals(current.role()) && !"SEEKER".equals(current.role()) && !"ADMIN".equals(current.role())) {
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
