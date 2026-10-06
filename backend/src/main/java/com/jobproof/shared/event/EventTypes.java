package com.jobproof.shared.event;

public final class EventTypes {

    private EventTypes() {
    }

    public static final String AUTHORIZATION_REVOKED = "AuthorizationRevoked";
    public static final String DELETION_REQUESTED = "DeletionRequested";
    public static final String TASK_COMPLETED = "TaskCompleted";
    public static final String TASK_FAILED = "TaskFailed";
    public static final String NOTIFICATION_REQUESTED = "NotificationRequested";
    public static final String CHANGELOG_PUBLISHED = "ChangelogPublished";
}
