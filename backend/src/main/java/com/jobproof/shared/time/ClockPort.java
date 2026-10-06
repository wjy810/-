package com.jobproof.shared.time;

import java.time.Instant;

public interface ClockPort {
    Instant now();
}
