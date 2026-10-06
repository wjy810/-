package com.jobproof.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "jobproof")
public class JobProofProperties {

    private final Cookie cookie = new Cookie();
    private final Worker worker = new Worker();
    private final Redis redis = new Redis();
    private final Dev dev = new Dev();
    private final Cors cors = new Cors();
    private final Ai ai = new Ai();
    private final Verification verification = new Verification();
    private final Policies policies = new Policies();
    private final Operator operator = new Operator();

    public Cookie getCookie() {
        return cookie;
    }

    public Worker getWorker() {
        return worker;
    }

    public Redis getRedis() {
        return redis;
    }

    public Dev getDev() {
        return dev;
    }

    public Cors getCors() {
        return cors;
    }

    public Ai getAi() {
        return ai;
    }

    public Verification getVerification() {
        return verification;
    }

    public Policies getPolicies() {
        return policies;
    }

    public Operator getOperator() {
        return operator;
    }

    public static class Cookie {
        private String name = "jobproof_session";
        private boolean secure = true;
        private String sameSite = "Lax";
        private int maxAgeDays = 14;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public boolean isSecure() {
            return secure;
        }

        public void setSecure(boolean secure) {
            this.secure = secure;
        }

        public String getSameSite() {
            return sameSite;
        }

        public void setSameSite(String sameSite) {
            this.sameSite = sameSite;
        }

        public int getMaxAgeDays() {
            return maxAgeDays;
        }

        public void setMaxAgeDays(int maxAgeDays) {
            this.maxAgeDays = maxAgeDays;
        }
    }

    public static class Worker {
        private boolean inProcess = true;
        private long pollMs = 1000;
        /** How long a claimed task stays reserved without a heartbeat before another worker may take it. */
        private long taskLeaseSeconds = 120;
        /** How often this process extends the leases of the tasks it is running. */
        private long leaseHeartbeatMs = 30_000;

        public long getTaskLeaseSeconds() { return taskLeaseSeconds; }
        public void setTaskLeaseSeconds(long value) { this.taskLeaseSeconds = Math.max(10, value); }
        public long getLeaseHeartbeatMs() { return leaseHeartbeatMs; }
        public void setLeaseHeartbeatMs(long value) { this.leaseHeartbeatMs = Math.max(1000, value); }

        public boolean isInProcess() {
            return inProcess;
        }

        public void setInProcess(boolean inProcess) {
            this.inProcess = inProcess;
        }

        public long getPollMs() {
            return pollMs;
        }

        public void setPollMs(long pollMs) {
            this.pollMs = pollMs;
        }
    }

    public static class Redis {
        private boolean enabled;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class Dev {
        private boolean mailboxEnabled;

        public boolean isMailboxEnabled() {
            return mailboxEnabled;
        }

        public void setMailboxEnabled(boolean mailboxEnabled) {
            this.mailboxEnabled = mailboxEnabled;
        }
    }

    public static class Cors {
        private List<String> origins = new ArrayList<>();

        public List<String> getOrigins() {
            return origins;
        }

        public void setOrigins(List<String> origins) {
            this.origins = origins;
        }
    }

    public static class Ai {
        private boolean llmParseEnabled;
        private String apiKey = "";
        private int autoRetryLimit = 2;

        public boolean isLlmParseEnabled() {
            return llmParseEnabled;
        }

        public void setLlmParseEnabled(boolean llmParseEnabled) {
            this.llmParseEnabled = llmParseEnabled;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public int getAutoRetryLimit() {
            return autoRetryLimit;
        }

        public void setAutoRetryLimit(int autoRetryLimit) {
            this.autoRetryLimit = autoRetryLimit;
        }
    }

    public static class Verification {
        private boolean enabled;
        private String emailProvider = "disabled";
        private String smsProvider = "disabled";
        private long codeTtlMinutes = 10;
        private long tokenTtlMinutes = 10;
        private long resendCooldownSeconds = 60;
        private int maxFailedAttempts = 5;
        private String rateLimitMode = "redis";
        private String contactHmacSecret = "";
        private String codeHmacSecret = "";
        private int emailAddressDailyLimit = 20;
        private int emailIpHourlyLimit = 50;
        private int smsAddressDailyLimit = 5;
        private int smsIpHourlyLimit = 10;
        private boolean legacyTestCompatibilityEnabled;
        private final QqSmtp qqSmtp = new QqSmtp();
        private final Aliyun aliyun = new Aliyun();

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getEmailProvider() { return emailProvider; }
        public void setEmailProvider(String emailProvider) { this.emailProvider = emailProvider; }
        public String getSmsProvider() { return smsProvider; }
        public void setSmsProvider(String smsProvider) { this.smsProvider = smsProvider; }
        public long getCodeTtlMinutes() { return codeTtlMinutes; }
        public void setCodeTtlMinutes(long codeTtlMinutes) { this.codeTtlMinutes = codeTtlMinutes; }
        public long getTokenTtlMinutes() { return tokenTtlMinutes; }
        public void setTokenTtlMinutes(long tokenTtlMinutes) { this.tokenTtlMinutes = tokenTtlMinutes; }
        public long getResendCooldownSeconds() { return resendCooldownSeconds; }
        public void setResendCooldownSeconds(long resendCooldownSeconds) { this.resendCooldownSeconds = resendCooldownSeconds; }
        public int getMaxFailedAttempts() { return maxFailedAttempts; }
        public void setMaxFailedAttempts(int maxFailedAttempts) { this.maxFailedAttempts = maxFailedAttempts; }
        public String getRateLimitMode() { return rateLimitMode; }
        public void setRateLimitMode(String rateLimitMode) { this.rateLimitMode = rateLimitMode; }
        public String getContactHmacSecret() { return contactHmacSecret; }
        public void setContactHmacSecret(String contactHmacSecret) { this.contactHmacSecret = contactHmacSecret; }
        public String getCodeHmacSecret() { return codeHmacSecret; }
        public void setCodeHmacSecret(String codeHmacSecret) { this.codeHmacSecret = codeHmacSecret; }
        public int getEmailAddressDailyLimit() { return emailAddressDailyLimit; }
        public void setEmailAddressDailyLimit(int value) { this.emailAddressDailyLimit = value; }
        public int getEmailIpHourlyLimit() { return emailIpHourlyLimit; }
        public void setEmailIpHourlyLimit(int value) { this.emailIpHourlyLimit = value; }
        public int getSmsAddressDailyLimit() { return smsAddressDailyLimit; }
        public void setSmsAddressDailyLimit(int value) { this.smsAddressDailyLimit = value; }
        public int getSmsIpHourlyLimit() { return smsIpHourlyLimit; }
        public void setSmsIpHourlyLimit(int value) { this.smsIpHourlyLimit = value; }
        public boolean isLegacyTestCompatibilityEnabled() { return legacyTestCompatibilityEnabled; }
        public void setLegacyTestCompatibilityEnabled(boolean value) { this.legacyTestCompatibilityEnabled = value; }
        public QqSmtp getQqSmtp() { return qqSmtp; }
        public Aliyun getAliyun() { return aliyun; }

        public static class QqSmtp {
            private String from = "";
            private String fromName = "JobProof AI";
            public String getFrom() { return from; }
            public void setFrom(String from) { this.from = from; }
            public String getFromName() { return fromName; }
            public void setFromName(String fromName) { this.fromName = fromName; }
        }

        public static class Aliyun {
            private String endpoint = "dypnsapi.aliyuncs.com";
            private String accessKeyId = "";
            private String accessKeySecret = "";
            private String signName = "";
            private String templateCode = "";
            public String getEndpoint() { return endpoint; }
            public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
            public String getAccessKeyId() { return accessKeyId; }
            public void setAccessKeyId(String value) { this.accessKeyId = value; }
            public String getAccessKeySecret() { return accessKeySecret; }
            public void setAccessKeySecret(String value) { this.accessKeySecret = value; }
            public String getSignName() { return signName; }
            public void setSignName(String value) { this.signName = value; }
            public String getTemplateCode() { return templateCode; }
            public void setTemplateCode(String value) { this.templateCode = value; }
        }
    }

    public static class Policies {
        private boolean registrationEnabled;
        private String termsVersion = "2026-08-26-v1";
        private String privacyVersion = "2026-08-26-v1";
        /** UTF-8 Markdown files with the operator's official texts; unset → the built-in reference text. */
        private String termsPath = "";
        private String privacyPath = "";
        /** When true, registration stays closed until both official texts are configured. */
        private boolean requirePublished;
        public boolean isRegistrationEnabled() { return registrationEnabled; }
        public void setRegistrationEnabled(boolean value) { this.registrationEnabled = value; }
        public String getTermsVersion() { return termsVersion; }
        public void setTermsVersion(String value) { this.termsVersion = value; }
        public String getPrivacyVersion() { return privacyVersion; }
        public void setPrivacyVersion(String value) { this.privacyVersion = value; }
        public String getTermsPath() { return termsPath; }
        public void setTermsPath(String value) { this.termsPath = value == null ? "" : value.trim(); }
        public String getPrivacyPath() { return privacyPath; }
        public void setPrivacyPath(String value) { this.privacyPath = value == null ? "" : value.trim(); }
        public boolean isRequirePublished() { return requirePublished; }
        public void setRequirePublished(boolean value) { this.requirePublished = value; }

        /** Both official texts are configured and readable. */
        public boolean isPublished() { return readable(termsPath) && readable(privacyPath); }

        /** Registration is open only when enabled and, if required, the official texts exist. */
        public boolean isRegistrationOpen() { return registrationEnabled && (!requirePublished || isPublished()); }

        private static boolean readable(String path) {
            return !path.isBlank() && java.nio.file.Files.isReadable(java.nio.file.Path.of(path));
        }
    }

    /** Who runs this deployment; shown in the footer, legal pages and error help. Blank values are hidden. */
    public static class Operator {
        private String name = "";
        private String icpRecord = "";
        private String publicSecurityRecord = "";
        private String contactEmail = "";
        private String contactPhone = "";
        private String address = "";
        private String feedbackUrl = "";
        public String getName() { return name; }
        public void setName(String value) { this.name = clean(value); }
        public String getIcpRecord() { return icpRecord; }
        public void setIcpRecord(String value) { this.icpRecord = clean(value); }
        public String getPublicSecurityRecord() { return publicSecurityRecord; }
        public void setPublicSecurityRecord(String value) { this.publicSecurityRecord = clean(value); }
        public String getContactEmail() { return contactEmail; }
        public void setContactEmail(String value) { this.contactEmail = clean(value); }
        public String getContactPhone() { return contactPhone; }
        public void setContactPhone(String value) { this.contactPhone = clean(value); }
        public String getAddress() { return address; }
        public void setAddress(String value) { this.address = clean(value); }
        public String getFeedbackUrl() { return feedbackUrl; }
        public void setFeedbackUrl(String value) { this.feedbackUrl = clean(value); }
        private static String clean(String value) { return value == null ? "" : value.trim(); }
    }
}
