package com.jobproof.modules.aibilling.application;

import java.util.UUID;
import java.util.function.Supplier;

public interface AccountLock { <T> T withLock(UUID accountId, Supplier<T> work); }