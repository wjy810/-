package com.jobproof.modules.notification.domain;

/** Where notification emails go out: SMTP in production, an in-memory outbox in development. */
public interface NotificationEmailPort {

    String providerCode();

    /** False when the deployment has no working sender; preferences then say email is not available. */
    boolean available();

    /** Sends one message or throws; the outbox retries. */
    void send(NotificationEmail email);

    record NotificationEmail(String to, String subject, String html, String text) {
    }
}
