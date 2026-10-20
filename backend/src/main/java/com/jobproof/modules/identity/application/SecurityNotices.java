package com.jobproof.modules.identity.application;

import com.jobproof.modules.notification.application.NotificationService;
import com.jobproof.modules.notification.domain.NotificationType;
import com.jobproof.shared.id.Ids;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Component;

/**
 * 账号安全通知: what changed on the account and when, each with "if this was not you" advice. They always show in
 * the notification center and go to the verified email when there is one; a failure to notify never fails the
 * change itself (the notification service swallows its own errors).
 */
@Component
public class SecurityNotices {

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.of("Asia/Shanghai"));
    private static final String ACTION = "/account";
    private static final String NOT_YOU = "如果不是你本人操作，请立即修改密码，并在“账号与安全”里退出其他设备。";

    private final NotificationService notifications;

    public SecurityNotices(NotificationService notifications) {
        this.notifications = notifications;
    }

    void passwordChanged(String accountId, Instant at) {
        send(accountId, "password-changed", "账号密码已修改",
                "你的密码在 " + WHEN.format(at) + " 修改，所有设备都已退出登录。" + "如果不是你本人操作，请立即通过“忘记密码”重置。");
    }

    void passwordReset(String accountId, Instant at) {
        send(accountId, "password-reset", "账号密码已通过找回重置",
                "你的密码在 " + WHEN.format(at) + " 通过验证码找回重置，所有设备都已退出登录。" + "如果不是你本人操作，请立即再次重置密码。");
    }

    void otherDevicesSignedOut(String accountId, int count, Instant at) {
        send(accountId, "devices-signed-out", count == 1 ? "已退出一台登录设备" : "已退出其他 " + count + " 台登录设备",
                WHEN.format(at) + " 在“账号与安全”里退出了" + (count == 1 ? "一台" : " " + count + " 台") + "其他设备上的登录。" + NOT_YOU);
    }

    void newDevice(String accountId, String client, Instant at) {
        send(accountId, "new-device", "新设备登录了你的账号",
                client + " 在 " + WHEN.format(at) + " 登录了你的账号。" + NOT_YOU);
    }

    void contactChanged(String accountId, String kind, String before, String after, Instant at) {
        String title = before == null ? "已绑定" + kind : kind + "已更换";
        String body = before == null
                ? WHEN.format(at) + " 为账号绑定了" + kind + " " + after + "，之后可以用它登录和接收提醒。"
                : WHEN.format(at) + " 把账号的" + kind + "从 " + before + " 改为 " + after + "。";
        send(accountId, "contact-changed", title, body + NOT_YOU);
    }

    private void send(String accountId, String kind, String title, String body) {
        notifications.request(accountId, NotificationType.SECURITY, "security:" + kind + ":" + Ids.newId(), title, body, ACTION);
    }
}
