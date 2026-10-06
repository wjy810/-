package com.jobproof.modules.identity.infra;

import com.jobproof.infrastructure.config.JobProofProperties;
import com.jobproof.modules.identity.application.VerificationHasher;
import com.jobproof.modules.identity.domain.VerificationChannel;
import com.jobproof.modules.identity.domain.VerificationDeliveryMessage;
import com.jobproof.modules.identity.domain.VerificationDeliveryPort;
import com.jobproof.modules.identity.domain.VerificationDeliveryReceipt;
import com.jobproof.shared.error.AppException;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "jobproof.verification.email-provider", havingValue = "qq-smtp")
public class QqSmtpVerificationDeliveryAdapter implements VerificationDeliveryPort {

    private final JavaMailSender mailSender;
    private final JobProofProperties properties;
    private final VerificationHasher hasher;
    private final String username;
    private final String password;

    public QqSmtpVerificationDeliveryAdapter(
            JavaMailSender mailSender,
            JobProofProperties properties,
            VerificationHasher hasher,
            @Value("${spring.mail.username:}") String username,
            @Value("${spring.mail.password:}") String password) {
        this.mailSender = mailSender;
        this.properties = properties;
        this.hasher = hasher;
        this.username = clean(username);
        this.password = clean(password);
    }

    @Override
    public String providerCode() {
        return "qq-smtp";
    }

    @Override
    public boolean supports(VerificationChannel channel) {
        return channel == VerificationChannel.EMAIL;
    }

    @Override
    public boolean available() {
        String from = clean(properties.getVerification().getQqSmtp().getFrom());
        return !username.isEmpty()
                && !password.isEmpty()
                && !from.isEmpty()
                && username.equalsIgnoreCase(from)
                && hasher.contactSecretConfigured()
                && hasher.codeSecretConfigured();
    }

    @Override
    public VerificationDeliveryReceipt deliver(VerificationDeliveryMessage message) {
        if (!available()) throw unavailable();
        try {
            MimeMessage mail = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mail, false, StandardCharsets.UTF_8.name());
            var qq = properties.getVerification().getQqSmtp();
            helper.setFrom(qq.getFrom(), qq.getFromName());
            helper.setTo(message.destination());
            helper.setSubject("【JobProof AI】身份验证码");
            helper.setText(html(message), true);
            mailSender.send(mail);
            return VerificationDeliveryReceipt.local();
        } catch (Exception exception) {
            throw unavailable();
        }
    }

    private static String html(VerificationDeliveryMessage message) {
        String action = message.purpose() == null ? "身份验证" : switch (message.purpose()) {
            case REGISTER -> "创建 JobProof AI 账号";
            case LOGIN_RECOVERY -> "找回 JobProof AI 账号密码";
            case CHANGE_CONTACT -> "更新账号联系方式";
        };
        return """
                <div style="font-family:Arial,'Microsoft YaHei',sans-serif;color:#10234a;line-height:1.7">
                  <h2 style="margin:0 0 12px">JobProof AI 身份验证码</h2>
                  <p>您正在进行：%s</p>
                  <p style="font-size:32px;font-weight:700;letter-spacing:8px;margin:20px 0;color:#2457e8">%s</p>
                  <p>验证码 %d 分钟内有效，请勿转发给他人。</p>
                  <p style="color:#70809a">如果不是您本人操作，请忽略本邮件。</p>
                </div>
                """.formatted(action, message.code(), message.ttlMinutes());
    }

    private static AppException unavailable() {
        return AppException.dependency("VERIFICATION_PROVIDER_UNAVAILABLE", "邮件验证服务暂时不可用，请稍后重试");
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}

