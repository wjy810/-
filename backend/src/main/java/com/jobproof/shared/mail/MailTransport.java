package com.jobproof.shared.mail;

import java.util.Optional;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * The mail account outgoing email uses right now (docs/phase2/05): the one an administrator saved in
 * 后台 › 邮件设置, or else the deployment's environment. Verification codes and notification emails both
 * go through it, so changing the account in one place changes it everywhere.
 */
public interface MailTransport {

    /** Empty when no usable account is configured. */
    Optional<Account> current();

    /**
     * @param notifications whether notification emails (receipts, reminders, membership) may be sent;
     *                      verification codes do not depend on it
     */
    record Account(JavaMailSender sender, String from, String fromName, String source, boolean notifications) {
    }
}
