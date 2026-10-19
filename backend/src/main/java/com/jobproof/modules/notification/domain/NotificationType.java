package com.jobproof.modules.notification.domain;

public enum NotificationType {
    TASK_COMPLETED,
    TASK_FAILED,
    DATA_EXPORT_PROGRESS,
    DATA_DELETION_PROGRESS,
    PRODUCT_UPDATE,
    SECURITY,
    DATA_RIGHTS,
    APPLICATION_ACTIVITY,
    /** A due next step of an application (an interview tomorrow, a test tonight). */
    REMINDER,
    /** The PRO plan is about to end, or has ended. */
    MEMBERSHIP,
    /** Monday's summary of last week's applications and this week's next steps. */
    WEEKLY_DIGEST
}
