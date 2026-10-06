package com.jobproof.modules.aibilling.application;

import java.util.function.Supplier;

public interface BillingTransaction {
    <T> T inTransaction(Supplier<T> work);
}
