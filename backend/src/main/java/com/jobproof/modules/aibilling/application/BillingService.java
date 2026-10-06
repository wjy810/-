package com.jobproof.modules.aibilling.application;

import com.jobproof.modules.aibilling.domain.*;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

public final class BillingService {
    private final BillingStore store;
    private final AccountLock lock;
    private final BillingTransaction transaction;
    private final Clock clock;

    public BillingService(BillingStore store, AccountLock lock, Clock clock) {
        this(store, lock, new BillingTransaction() {
            @Override
            public <T> T inTransaction(Supplier<T> work) {
                return work.get();
            }
        }, clock);
    }

    public BillingService(BillingStore store, AccountLock lock, BillingTransaction transaction, Clock clock) {
        this.store = store;
        this.lock = lock;
        this.transaction = transaction;
        this.clock = clock;
    }

    public BillingAuthorization authorize(UUID accountId, String chainId, Money estimate) {
        IdempotencyRequest request = request(accountId, "AUTHORIZE", chainId, estimate.amount().toPlainString());
        return lockedTransaction(accountId, () -> replay(request, BillingAuthorization.class).orElseGet(() -> {
            BillingAuthorization authorization = store.findAuthorizationByChargeChainId(chainId)
                    .filter(existing -> existing.accountId().equals(accountId))
                    .orElseGet(() -> createAuthorization(accountId, chainId, estimate));
            remember(request, authorization);
            return authorization;
        }));
    }

    public Settlement settle(UUID authorizationId, Money actual, String key) {
        BillingAuthorization initial = store.getAuthorization(authorizationId);
        IdempotencyRequest request = request(initial.accountId(), "SETTLE", key,
                authorizationId + ":" + actual.amount().toPlainString());
        return lockedTransaction(initial.accountId(), () -> replay(request, Settlement.class).map(Settlement::asDuplicate)
                .orElseGet(() -> settleOnce(initial.accountId(), authorizationId, actual, request)));
    }

    public WalletAccount creditAndRecover(UUID accountId, Money amount, LedgerEntry.Type type,
            String referenceId, String key) {
        if (type != LedgerEntry.Type.TOP_UP && type != LedgerEntry.Type.ADMIN_CREDIT) {
            throw new IllegalArgumentException("Unsupported credit type");
        }
        IdempotencyRequest request = request(accountId, type.name(), key,
                referenceId + ":" + amount.amount().toPlainString());
        return lockedTransaction(accountId, () -> replay(request, WalletAccount.class).orElseGet(() -> {
            WalletAccount before = store.getWallet(accountId);
            WalletAccount credited = before.credit(amount);
            Money debtBefore = credited.pendingDebit();
            WalletAccount after = credited.recoverPendingDebit();
            store.saveWallet(after, before.versionNo());
            append(after, type, amount, "wallet", referenceId, key);
            Money recovered = debtBefore.subtract(after.pendingDebit());
            if (recovered.isPositive()) append(after, LedgerEntry.Type.DEBT_RECOVERY, recovered, "wallet", referenceId, key + ":recovery");
            remember(request, after);
            return after;
        }));
    }

    public WalletAccount adminDebit(UUID accountId, Money amount, String referenceId, String key) {
        IdempotencyRequest request = request(accountId, "ADMIN_DEBIT", key,
                referenceId + ":" + amount.amount().toPlainString());
        return lockedTransaction(accountId, () -> replay(request, WalletAccount.class).orElseGet(() -> {
            WalletAccount before = store.getWallet(accountId);
            WalletAccount after = before.debit(amount);
            store.saveWallet(after, before.versionNo());
            append(after, LedgerEntry.Type.ADMIN_DEBIT, amount, "admin", referenceId, key);
            remember(request, after);
            return after;
        }));
    }

    public BillingAuthorization release(UUID authorizationId, String key) {
        BillingAuthorization initial = store.getAuthorization(authorizationId);
        IdempotencyRequest request = request(initial.accountId(), "RELEASE", key, authorizationId.toString());
        return lockedTransaction(initial.accountId(), () -> replay(request, BillingAuthorization.class).orElseGet(() -> {
            BillingAuthorization authorization = store.getAuthorization(authorizationId);
            BillingAuthorization released = releaseHeld(authorization, false, key);
            remember(request, released);
            return released;
        }));
    }

    public BillingAuthorization expire(UUID authorizationId) {
        BillingAuthorization initial = store.getAuthorization(authorizationId);
        String key = "expire:" + authorizationId;
        IdempotencyRequest request = request(initial.accountId(), "EXPIRE", key, authorizationId.toString());
        return lockedTransaction(initial.accountId(), () -> replay(request, BillingAuthorization.class).orElseGet(() -> {
            BillingAuthorization expired = releaseHeld(store.getAuthorization(authorizationId), true, key);
            remember(request, expired);
            return expired;
        }));
    }

    public Money refund(UUID accountId, UUID billingId, Money amount, String key) {
        IdempotencyRequest request = request(accountId, "REFUND", key,
                billingId + ":" + amount.amount().toPlainString());
        return lockedTransaction(accountId, () -> replay(request, Money.class).orElseGet(() -> {
            BillingRecord bill = store.findBilling(billingId).orElseThrow(() -> new IllegalArgumentException("Billing record not found"));
            if (!bill.accountId().equals(accountId)) throw new IllegalArgumentException("Billing record does not belong to account");
            BillingRecord refunded = bill.refund(amount);
            WalletAccount before = store.getWallet(accountId);
            WalletAccount after = before.credit(amount);
            store.saveWallet(after, before.versionNo());
            store.saveBilling(refunded);
            append(after, LedgerEntry.Type.REFUND, amount, "billing", billingId.toString(), key);
            remember(request, amount);
            return amount;
        }));
    }

    private BillingAuthorization createAuthorization(UUID accountId, String chainId, Money estimate) {
        WalletAccount before = store.getWallet(accountId);
        WalletAccount after = before.hold(estimate);
        store.saveWallet(after, before.versionNo());
        BillingAuthorization authorization = new BillingAuthorization(UUID.randomUUID(), accountId, chainId,
                estimate, BillingAuthorization.Status.HELD, Instant.now(clock));
        store.saveAuthorization(authorization);
        append(after, LedgerEntry.Type.HOLD, estimate, "authorization", authorization.id().toString(), "hold:" + chainId);
        return authorization;
    }

    private Settlement settleOnce(UUID accountId, UUID authorizationId, Money actual, IdempotencyRequest request) {
        BillingAuthorization authorization = store.getAuthorization(authorizationId);
        if (authorization.status() != BillingAuthorization.Status.HELD) throw new IllegalStateException("Authorization already finalized");
        WalletAccount before = store.getWallet(accountId);
        WalletAccount after = before;
        Money charged;
        Money pendingTopUp = Money.ZERO;
        if (actual.compareTo(authorization.heldAmount()) <= 0) {
            charged = actual;
            after = after.consumeFrozen(actual);
            Money release = authorization.heldAmount().subtract(actual);
            if (release.isPositive()) after = after.release(release);
        } else {
            after = after.consumeFrozen(authorization.heldAmount());
            charged = authorization.heldAmount();
            Money excess = actual.subtract(authorization.heldAmount());
            Money extra = after.available().compareTo(excess) < 0 ? after.available() : excess;
            if (extra.isPositive()) { after = after.debit(extra); charged = charged.add(extra); }
            pendingTopUp = excess.subtract(extra);
            if (pendingTopUp.isPositive()) after = after.addPendingDebit(pendingTopUp);
        }
        store.saveWallet(after, before.versionNo());
        store.saveAuthorization(authorization.settle());
        BillingRecord bill = new BillingRecord(UUID.randomUUID(), accountId, authorizationId, actual, Money.ZERO,
                pendingTopUp.isPositive() ? BillingRecord.Status.PAYMENT_REQUIRED : BillingRecord.Status.SETTLED);
        store.saveBilling(bill);
        append(after, LedgerEntry.Type.CHARGE, charged, "billing", bill.id().toString(), request.key());
        if (pendingTopUp.isPositive()) append(after, LedgerEntry.Type.ADJUSTMENT_CHARGE, pendingTopUp, "billing", bill.id().toString(), request.key() + ":pending");
        Settlement result = new Settlement(bill.id(), charged, pendingTopUp, false);
        remember(request, result);
        return result;
    }

    private BillingAuthorization releaseHeld(BillingAuthorization authorization, boolean expired, String key) {
        if (authorization.status() != BillingAuthorization.Status.HELD) return authorization;
        WalletAccount before = store.getWallet(authorization.accountId());
        WalletAccount after = before.release(authorization.heldAmount());
        store.saveWallet(after, before.versionNo());
        BillingAuthorization finalized = expired ? authorization.expire() : authorization.release();
        store.saveAuthorization(finalized);
        append(after, LedgerEntry.Type.RELEASE, authorization.heldAmount(), "authorization", authorization.id().toString(), key);
        return finalized;
    }

    private IdempotencyRequest request(UUID accountId, String operation, String key, String fingerprint) {
        return new IdempotencyRequest(accountId, operation, key, fingerprint);
    }

    private <T> Optional<T> replay(IdempotencyRequest request, Class<T> type) {
        Optional<IdempotencyRecord> existing = store.findIdempotency(request);
        if (existing.isEmpty()) return Optional.empty();
        IdempotencyRecord record = existing.get();
        if (!record.request().requestFingerprint().equals(request.requestFingerprint())) {
            throw new IdempotencyConflictException();
        }
        return Optional.of(type.cast(record.result()));
    }

    private void remember(IdempotencyRequest request, Object result) { store.saveIdempotency(new IdempotencyRecord(request, result)); }
    private <T> T lockedTransaction(UUID accountId, Supplier<T> work) { return lock.withLock(accountId, () -> transaction.inTransaction(work)); }
    private void append(WalletAccount wallet, LedgerEntry.Type type, Money amount, String refType, String refId, String key) {
        store.appendLedger(new LedgerEntry(UUID.randomUUID(), wallet.id(), type, amount, wallet.available(), refType,
                refId, key, wallet.versionNo(), Instant.now(clock)));
    }

    public record Settlement(UUID billingId, Money charged, Money pendingTopUp, boolean duplicate) {
        public Settlement asDuplicate() { return new Settlement(billingId, charged, pendingTopUp, true); }
        public Money pendingDebit() { return pendingTopUp; }
    }
    public static final class IdempotencyConflictException extends RuntimeException { }
}
