package com.jobproof.modules.airesume.application;

import java.time.Instant;

public record AiResumeMessageView(String id, long sequence, String role, String messageType, String status,
        String content, String errorCode, String model, long inputTokens, long outputTokens,
        Instant createdAt, Instant completedAt) {}
