package com.jobproof.modules.notification.infra;

import com.jobproof.modules.notification.domain.NotificationEmailPort;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/** Notification emails over the deployment's SMTP account (the same one that sends verification codes). */
@Component
@ConditionalOnProperty(name = "jobproof.notification.email.provider", havingValue = "smtp")
public class SmtpNotificationEmailAdapter implements NotificationEmailPort {

    private final JavaMailSender mailSender;
    private final String username;
    private final String from;
    private final String fromName;

    public SmtpNotificationEmailAdapter(JavaMailSender mailSender,
            @Value("${spring.mail.username:}") String username,
            @Value("${jobproof.notification.email.from:}") String from,
            @Value("${jobproof.notification.email.from-name:JobProof AI}") String fromName) {
        this.mailSender = mailSender;
        this.username = username == null ? "" : username.trim();
        this.from = from == null ? "" : from.trim();
        this.fromName = fromName;
    }

    @Override public String providerCode() { return "smtp"; }

    @Override
    public boolean available() {
        return !username.isEmpty() && !from.isEmpty();
    }

    @Override
    public void send(NotificationEmail email) {
        try {
            MimeMessage mail = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mail, true, StandardCharsets.UTF_8.name());
            helper.setFrom(from, fromName);
            helper.setTo(email.to());
            helper.setSubject(email.subject());
            helper.setText(email.text(), email.html());
            mailSender.send(mail);
        } catch (Exception exception) {
            throw new IllegalStateException("notification email not accepted by SMTP", exception);
        }
    }
}
