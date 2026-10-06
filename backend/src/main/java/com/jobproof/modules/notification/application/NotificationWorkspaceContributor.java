package com.jobproof.modules.notification.application;

import com.jobproof.shared.auth.CurrentAccount;
import com.jobproof.shared.workspace.WorkspaceSummaryContributor;
import org.springframework.stereotype.Component;

/** 工作台：未读通知数。 */
@Component
class NotificationWorkspaceContributor implements WorkspaceSummaryContributor {

    private final NotificationService notifications;

    NotificationWorkspaceContributor(NotificationService notifications) {
        this.notifications = notifications;
    }

    @Override
    public String section() {
        return "unreadNotifications";
    }

    @Override
    public Object summarize(CurrentAccount current) {
        return notifications.unreadCount(current.accountId());
    }
}
