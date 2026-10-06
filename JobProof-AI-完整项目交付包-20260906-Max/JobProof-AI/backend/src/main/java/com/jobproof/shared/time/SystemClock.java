package com.jobproof.shared.time;

import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public class SystemClock implements ClockPort {
    @Override
    public Instant now() {
        return Instant.now();
    }
}
