package com.jobproof.modules.task.domain;

public enum TaskStatus {
    PENDING,
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELLED;

    public boolean terminal() {
        return this == SUCCEEDED || this == FAILED || this == CANCELLED;
    }

    public boolean cancellable() {
        return this == PENDING || this == RUNNING;
    }
}
