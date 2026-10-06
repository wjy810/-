package com.jobproof.modules.aibilling.domain;

import java.util.UUID;

public record WalletAccount(UUID id, Money available, Money frozen, Money pendingDebit, long versionNo, boolean systemChannelsSuspended) {
    public WalletAccount {
        if (available.isNegative() || frozen.isNegative() || pendingDebit.isNegative()) throw new IllegalArgumentException("Wallet balances cannot be negative");
        if (systemChannelsSuspended != pendingDebit.isPositive()) throw new IllegalArgumentException("Suspension must reflect pending debit");
    }
    public WalletAccount credit(Money value) { positive(value); return next(available.add(value), frozen, pendingDebit); }
    public WalletAccount debit(Money value) { positive(value); if (available.compareTo(value) < 0) throw new InsufficientFundsException(); return next(available.subtract(value), frozen, pendingDebit); }
    public WalletAccount hold(Money value) { positive(value); if (systemChannelsSuspended || available.compareTo(value) < 0) throw new InsufficientFundsException(); return next(available.subtract(value), frozen.add(value), pendingDebit); }
    public WalletAccount release(Money value) { positive(value); if (frozen.compareTo(value) < 0) throw new IllegalStateException("Release exceeds frozen balance"); return next(available.add(value), frozen.subtract(value), pendingDebit); }
    public WalletAccount consumeFrozen(Money value) { positive(value); if (frozen.compareTo(value) < 0) throw new IllegalStateException("Charge exceeds frozen balance"); return next(available, frozen.subtract(value), pendingDebit); }
    public WalletAccount addPendingDebit(Money value) { positive(value); return next(available, frozen, pendingDebit.add(value)); }
    public WalletAccount recoverPendingDebit() { Money recovered = available.compareTo(pendingDebit) < 0 ? available : pendingDebit; return next(available.subtract(recovered), frozen, pendingDebit.subtract(recovered)); }
    private WalletAccount next(Money nextAvailable, Money nextFrozen, Money nextPending) { return new WalletAccount(id, nextAvailable, nextFrozen, nextPending, versionNo + 1, nextPending.isPositive()); }
    private static void positive(Money value) { if (!value.isPositive()) throw new IllegalArgumentException("Amount must be positive"); }
    public static final class InsufficientFundsException extends RuntimeException { }
}