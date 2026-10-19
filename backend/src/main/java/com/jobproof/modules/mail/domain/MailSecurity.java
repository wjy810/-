package com.jobproof.modules.mail.domain;

/** How the connection to the SMTP server is protected. */
public enum MailSecurity {
    /** TLS from the first byte, usually port 465. */
    SSL,
    /** Plain connection upgraded with STARTTLS, usually port 587. */
    STARTTLS,
    /** No encryption; only for a relay inside a private network. */
    NONE
}
