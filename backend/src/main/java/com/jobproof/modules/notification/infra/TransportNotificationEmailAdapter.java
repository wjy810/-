package com.jobproof.modules.notification.infra;

import com.jobproof.modules.notification.domain.NotificationEmailPort;
import com.jobproof.shared.mail.MailTransport;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/**
 * Notification emails through the shared mail account (后台 › 邮件设置, or the environment). Whether they
 * go out at all follows that account's "提醒邮件" switch; the dev provider replaces this adapter.
 */
@Component
@ConditionalOnExpression("'${jobproof.notification.email.provider:none}' != 'dev'")
public class TransportNotificationEmailAdapter implements NotificationEmailPort {

    private final MailTransport transport;

    public TransportNotificationEmailAdapter(MailTransport transport) {
        this.transport = transport;
    }

    @Override public String providerCode() { return "smtp"; }

    @Override
    public boolean available() {
        return transport.current().map(MailTransport.Account::notifications).orElse(false);
    }

    @Override
    public void send(NotificationEmail email) {
        MailTransport.Account account = transport.current()
                .filter(MailTransport.Account::notifications)
                .orElseThrow(() -> new IllegalStateException("notification emails are switched off"));
        try {
            MimeMessage mail = account.sender().createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mail, true, StandardCharsets.UTF_8.name());
            helper.setFrom(account.from(), account.fromName());
            helper.setTo(email.to());
            helper.setSubject(email.subject());
            helper.setText(email.text(), email.html());
            account.sender().send(mail);
        } catch (Exception exception) {
            throw new IllegalStateException("notification email not accepted by SMTP", exception);
        }
    }
}
