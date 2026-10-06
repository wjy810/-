package com.jobproof.modules.aibilling.application;

import static org.junit.jupiter.api.Assertions.*;
import com.jobproof.modules.aibilling.domain.*;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class BillingServiceTest {
    @Test void duplicateSettlementReplaysPendingTopUpAndRejectsFingerprintConflict() {
        MemoryStore store = new MemoryStore(Money.usd("6")); BillingService service = service(store);
        BillingAuthorization auth = service.authorize(store.wallet.id(), "chain", Money.usd("5"));
        BillingService.Settlement first = service.settle(auth.id(), Money.usd("8"), "settle");
        BillingService.Settlement replay = service.settle(auth.id(), Money.usd("8"), "settle");
        assertEquals(first.billingId(), replay.billingId()); assertEquals(Money.usd("2"), replay.pendingTopUp()); assertTrue(replay.duplicate());
        assertThrows(BillingService.IdempotencyConflictException.class, () -> service.settle(auth.id(), Money.usd("7"), "settle"));
    }
    @Test void sameKeyIsScopedByAccountAndOperation() {
        MemoryStore a = new MemoryStore(Money.usd("20")); MemoryStore b = new MemoryStore(Money.usd("20"));
        BillingService sa = service(a); BillingService sb = service(b);
        sa.adminDebit(a.wallet.id(), Money.usd("1"), "one", "shared");
        sb.adminDebit(b.wallet.id(), Money.usd("2"), "two", "shared");
        sa.creditAndRecover(a.wallet.id(), Money.usd("3"), LedgerEntry.Type.TOP_UP, "topup", "shared");
        assertEquals(Money.usd("22"), a.wallet.available()); assertEquals(Money.usd("18"), b.wallet.available());
    }
    @Test void expireReleasesFrozenBalanceAtomically() {
        MemoryStore store = new MemoryStore(Money.usd("10")); BillingService service = service(store);
        BillingAuthorization auth = service.authorize(store.wallet.id(), "exp", Money.usd("4"));
        assertEquals(BillingAuthorization.Status.EXPIRED, service.expire(auth.id()).status());
        assertEquals(Money.usd("10"), store.wallet.available()); assertEquals(Money.ZERO, store.wallet.frozen()); assertTrue(store.transactions >= 2);
    }
    @Test void refundChecksOwnershipCumulativeLimitAndIdempotency() {
        MemoryStore store = new MemoryStore(Money.usd("10")); BillingService service = service(store);
        BillingAuthorization auth = service.authorize(store.wallet.id(), "bill", Money.usd("5"));
        UUID bill = service.settle(auth.id(), Money.usd("5"), "settle").billingId();
        service.refund(store.wallet.id(), bill, Money.usd("2"), "r1");
        service.refund(store.wallet.id(), bill, Money.usd("2"), "r1");
        assertThrows(IllegalArgumentException.class, () -> service.refund(UUID.randomUUID(), bill, Money.usd("1"), "other"));
        assertThrows(IllegalArgumentException.class, () -> service.refund(store.wallet.id(), bill, Money.usd("4"), "r2"));
        assertEquals(Money.usd("7"), store.wallet.available());
    }
    private static BillingService service(MemoryStore store) {
        AccountLock lock = new AccountLock() { public <T> T withLock(UUID id, Supplier<T> work) { return work.get(); } };
        BillingTransaction tx = new BillingTransaction() { public <T> T inTransaction(Supplier<T> work) { store.transactions++; return work.get(); } };
        return new BillingService(store, lock, tx, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
    }
    private static final class MemoryStore implements BillingStore {
        WalletAccount wallet; final Map<UUID,BillingAuthorization> auth = new HashMap<>(); final Map<UUID,BillingRecord> bills = new HashMap<>();
        final Map<String,IdempotencyRecord> requests = new HashMap<>(); final List<LedgerEntry> ledger = new ArrayList<>(); int transactions;
        MemoryStore(Money amount) { wallet = new WalletAccount(UUID.randomUUID(), amount, Money.ZERO, Money.ZERO, 0, false); }
        public WalletAccount getWallet(UUID id) { if (!wallet.id().equals(id)) throw new NoSuchElementException(); return wallet; }
        public void saveWallet(WalletAccount value, long expected) { if (wallet.versionNo() != expected) throw new IllegalStateException("version conflict"); wallet = value; }
        public void appendLedger(LedgerEntry entry) { ledger.add(entry); }
        public BillingAuthorization getAuthorization(UUID id) { return Optional.ofNullable(auth.get(id)).orElseThrow(); }
        public void saveAuthorization(BillingAuthorization value) { auth.put(value.id(), value); }
        public Optional<BillingAuthorization> findAuthorizationByChargeChainId(String id) { return auth.values().stream().filter(a -> a.chargeChainId().equals(id)).findFirst(); }
        public Optional<IdempotencyRecord> findIdempotency(IdempotencyRequest request) { return Optional.ofNullable(requests.get(scope(request))); }
        public void saveIdempotency(IdempotencyRecord record) { requests.put(scope(record.request()), record); }
        public Optional<BillingRecord> findBilling(UUID id) { return Optional.ofNullable(bills.get(id)); }
        public void saveBilling(BillingRecord bill) { bills.put(bill.id(), bill); }
        private String scope(IdempotencyRequest request) { return request.accountId() + ":" + request.operation() + ":" + request.key(); }
    }
}
