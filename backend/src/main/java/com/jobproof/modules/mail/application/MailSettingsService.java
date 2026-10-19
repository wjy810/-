package com.jobproof.modules.mail.application;

import com.jobproof.modules.audit.application.AuditService;
import com.jobproof.modules.mail.domain.MailSecurity;
import com.jobproof.shared.auth.CurrentAccount;
import com.jobproof.shared.concurrency.Versions;
import com.jobproof.shared.error.AppException;
import com.jobproof.shared.mail.MailTransport;
import com.jobproof.shared.time.ClockPort;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 后台邮件设置 (docs/phase2/05). An administrator can save the SMTP account here; while a saved account
 * exists, all outgoing mail (verification codes and notification emails) uses it, otherwise mail keeps
 * using the environment variables. The password is encrypted and never leaves the server.
 */
@Service
public class MailSettingsService implements MailTransport {

    static final String ROW = "default";
    private static final Duration TEST_INTERVAL = Duration.ofSeconds(10);
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]{1,64}@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final Pattern HOST = Pattern.compile("^[A-Za-z0-9](?:[A-Za-z0-9.-]{0,253}[A-Za-z0-9])?$");

    private final JdbcTemplate jdbc;
    private final ClockPort clock;
    private final MailSecretCipher cipher;
    private final AuditService audit;
    private final ObjectProvider<JavaMailSender> environmentSender;
    private final Environment environment;
    /** The sender built from the saved settings, rebuilt only when they change. */
    private final AtomicReference<Built> built = new AtomicReference<>();
    private volatile Instant lastTestAt;
    private volatile TestResult environmentTest;

    public MailSettingsService(JdbcTemplate jdbc, ClockPort clock, MailSecretCipher cipher, AuditService audit,
            ObjectProvider<JavaMailSender> environmentSender,
            @Value("${spring.mail.host:}") String envHost,
            @Value("${spring.mail.username:}") String envUsername,
            @Value("${spring.mail.password:}") String envPassword,
            @Value("${jobproof.notification.email.from:}") String notificationFrom,
            @Value("${jobproof.verification.qq-smtp.from:}") String verificationFrom,
            @Value("${jobproof.notification.email.from-name:JobProof AI}") String envFromName,
            @Value("${jobproof.notification.email.provider:none}") String notificationProvider) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.cipher = cipher;
        this.audit = audit;
        this.environmentSender = environmentSender;
        String envFrom = trim(notificationFrom).isEmpty() ? trim(verificationFrom) : trim(notificationFrom);
        this.environment = new Environment(trim(envHost), trim(envUsername), !trim(envPassword).isEmpty(), envFrom,
                trim(envFromName).isEmpty() ? "JobProof AI" : trim(envFromName), "smtp".equalsIgnoreCase(trim(notificationProvider)));
    }

    // ---------------------------------------------------------------- MailTransport

    @Override
    public Optional<Account> current() {
        Optional<Stored> stored = stored();
        if (stored.isPresent()) return adminAccount(stored.get());
        return environmentAccount();
    }

    private Optional<Account> adminAccount(Stored stored) {
        Built cached = built.get();
        if (cached == null || cached.version() != stored.version()) {
            String password;
            try {
                password = cipher.decrypt(stored.passwordCiphertext());
            } catch (IllegalStateException unreadable) {
                // Saved settings that cannot be read fail closed: mail stops rather than quietly
                // switching to another account.
                return Optional.empty();
            }
            cached = new Built(stored.version(), sender(stored, password));
            built.set(cached);
        }
        return Optional.of(new Account(cached.sender(), stored.fromAddress(), stored.fromName(), "ADMIN", stored.notifications()));
    }

    private Optional<Account> environmentAccount() {
        Environment env = environment;
        if (env.username().isEmpty() || !env.passwordSet() || env.from().isEmpty()) return Optional.empty();
        if (requiresMatchingSender(env.host()) && !env.from().equalsIgnoreCase(env.username())) return Optional.empty();
        JavaMailSender sender = environmentSender.getIfAvailable();
        if (sender == null) return Optional.empty();
        return Optional.of(new Account(sender, env.from(), env.fromName(), "ENVIRONMENT", env.notifications()));
    }

    // ---------------------------------------------------------------- administration

    @Transactional(readOnly = true)
    public SettingsView view(CurrentAccount current) {
        assertAdmin(current);
        Optional<Stored> stored = stored();
        Optional<Account> account = current();
        AdminView admin = stored.map(value -> new AdminView(value.host(), value.port(), value.security().name(), value.username(),
                value.fromAddress(), value.fromName(), value.notifications(), value.version(), value.updatedAt(),
                account.isPresent())).orElse(null);
        Environment env = environment;
        EnvironmentView environmentView = new EnvironmentView(environmentAccount().isPresent(), env.host(), mask(env.username()),
                env.from(), env.notifications());
        TestResult test = stored.map(Stored::lastTest).orElse(environmentTest);
        String source = account.map(Account::source).orElse("NONE");
        return new SettingsView(source, account.map(Account::notifications).orElse(false), cipher.ready(), admin, environmentView,
                test, delivery());
    }

    @Transactional
    public SettingsView save(CurrentAccount current, SettingsCommand command) {
        assertAdmin(current);
        if (command == null) throw AppException.user("MAIL_SETTINGS_INVALID", "请填写邮件设置");
        String host = trim(command.host()).toLowerCase(Locale.ROOT);
        if (!HOST.matcher(host).matches()) throw AppException.user("MAIL_HOST_INVALID", "SMTP 服务器地址不正确，例如 smtp.qq.com");
        int port = command.port() == null ? 0 : command.port();
        if (port < 1 || port > 65535) throw AppException.user("MAIL_PORT_INVALID", "端口需要在 1 到 65535 之间");
        MailSecurity security = parseSecurity(command.security());
        String username = trim(command.username());
        if (username.isEmpty() || username.length() > 320) throw AppException.user("MAIL_USERNAME_INVALID", "请填写 SMTP 账号");
        String from = trim(command.fromAddress());
        if (!EMAIL.matcher(from).matches() || from.length() > 320) throw AppException.user("MAIL_FROM_INVALID", "发件地址不是有效的邮箱");
        if (requiresMatchingSender(host) && !from.equalsIgnoreCase(username)) {
            throw AppException.user("MAIL_FROM_MISMATCH", "QQ 邮箱和腾讯企业邮箱要求发件地址与登录账号一致");
        }
        String fromName = trim(command.fromName());
        if (fromName.isEmpty()) fromName = "JobProof AI";
        if (fromName.length() > 60 || fromName.matches(".*[\\r\\n<>\"].*")) throw AppException.user("MAIL_FROM_NAME_INVALID", "发件人名称最多 60 个字，不能包含引号和尖括号");
        String password = command.password() == null ? "" : command.password().trim();
        if (password.length() > 256) throw AppException.user("MAIL_PASSWORD_INVALID", "授权码过长");
        boolean notifications = Boolean.TRUE.equals(command.notifications());

        Optional<Stored> existing = stored();
        Versions.assertExpected(command.expectedVersion(), existing.map(Stored::version).orElse(0));
        String ciphertext;
        if (!password.isEmpty()) {
            if (!cipher.ready()) {
                throw AppException.conflict("MAIL_SECRET_KEY_MISSING", "服务器没有配置主密钥（JOBPROOF_AI_MASTER_KEY），无法安全保存授权码");
            }
            ciphertext = cipher.encrypt(password);
        } else if (existing.isPresent()) {
            ciphertext = existing.get().passwordCiphertext();
        } else {
            throw AppException.user("MAIL_PASSWORD_REQUIRED", "第一次保存需要填写授权码");
        }
        Instant now = clock.now();
        if (existing.isPresent()) {
            int changed = jdbc.update("UPDATE mail_settings SET host=?,port=?,security=?,username=?,password_ciphertext=?,from_address=?,from_name=?,notifications=?,version_no=version_no+1,updated_by=?,updated_at=? WHERE id=? AND version_no=?",
                    host, port, security.name(), username, ciphertext, from, fromName, notifications, current.accountId(), now, ROW, existing.get().version());
            if (changed == 0) throw AppException.conflict("VERSION_CONFLICT", "邮件设置刚被其他管理员修改，请刷新后再保存");
        } else {
            jdbc.update("INSERT INTO mail_settings(id,host,port,security,username,password_ciphertext,from_address,from_name,notifications,version_no,updated_by,updated_at) VALUES(?,?,?,?,?,?,?,?,?,1,?,?)",
                    ROW, host, port, security.name(), username, ciphertext, from, fromName, notifications, current.accountId(), now);
        }
        built.set(null);
        audit.append(current.accountId(), "MAIL_SETTINGS_UPDATED", "MAIL_SETTINGS", ROW,
                host + ":" + port + " " + security.name() + "，账号 " + mask(username) + (password.isEmpty() ? "" : "，已更新授权码")
                        + "，提醒邮件" + (notifications ? "开启" : "关闭"));
        return view(current);
    }

    /** Removes the saved account; mail goes back to the environment variables. */
    @Transactional
    public SettingsView clear(CurrentAccount current) {
        assertAdmin(current);
        if (jdbc.update("DELETE FROM mail_settings WHERE id=?", ROW) > 0) {
            built.set(null);
            audit.append(current.accountId(), "MAIL_SETTINGS_CLEARED", "MAIL_SETTINGS", ROW, "恢复为环境变量中的邮件配置");
        }
        return view(current);
    }

    /** Sends one test message through the account in use and reports what the server said. */
    public TestResult test(CurrentAccount current, String to) {
        assertAdmin(current);
        String address = trim(to);
        if (!EMAIL.matcher(address).matches()) throw AppException.user("MAIL_TEST_ADDRESS_INVALID", "请填写有效的收件邮箱");
        Instant now = clock.now();
        Instant last = lastTestAt;
        if (last != null && Duration.between(last, now).compareTo(TEST_INTERVAL) < 0) {
            throw AppException.rateLimited("MAIL_TEST_TOO_OFTEN", "测试太频繁了，请稍等几秒再试");
        }
        lastTestAt = now;
        Account account = current().orElseThrow(() -> AppException.conflict("MAIL_NOT_CONFIGURED", "还没有可用的邮件账号，请先保存邮件设置"));
        TestResult result;
        try {
            MimeMessage message = account.sender().createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(account.from(), account.fromName());
            helper.setTo(address);
            helper.setSubject("【JobProof AI】测试邮件");
            helper.setText("这是一封测试邮件。收到它说明 JobProof AI 的邮件设置可以正常发信。",
                    "<div style=\"font-family:-apple-system,'PingFang SC','Microsoft YaHei',Arial,sans-serif;color:#1f2430;line-height:1.7\">"
                            + "<h2 style=\"margin:0 0 12px;font-size:18px\">邮件设置可以正常发信</h2>"
                            + "<p style=\"margin:0;color:#3a3f4c\">这是一封来自 JobProof AI 后台的测试邮件，验证码和提醒邮件会通过同一个账号发出。</p></div>");
            account.sender().send(message);
            result = new TestResult(now, true, "测试邮件已发出，请到 " + mask(address) + " 查收（也看看垃圾邮件箱）");
        } catch (MailException | MessagingException | java.io.UnsupportedEncodingException failure) {
            result = new TestResult(now, false, explain(failure));
        }
        record(account, result);
        audit.append(current.accountId(), "MAIL_SETTINGS_TESTED", "MAIL_SETTINGS", ROW, (result.ok() ? "测试成功" : "测试失败：" + result.message()));
        return result;
    }

    private void record(Account account, TestResult result) {
        if ("ADMIN".equals(account.source())) {
            jdbc.update("UPDATE mail_settings SET last_test_at=?,last_test_ok=?,last_test_message=? WHERE id=?",
                    result.at(), result.ok(), clip(result.message(), 200), ROW);
        } else {
            environmentTest = result;
        }
    }

    // ---------------------------------------------------------------- internals

    private Optional<Stored> stored() {
        return jdbc.query("SELECT * FROM mail_settings WHERE id=?", this::row, ROW).stream().findFirst();
    }

    private Stored row(ResultSet rs, int n) throws SQLException {
        Timestamp tested = rs.getTimestamp("last_test_at");
        Object ok = rs.getObject("last_test_ok");
        TestResult test = tested == null ? null
                : new TestResult(tested.toInstant(), ok instanceof Boolean value ? value : Boolean.TRUE.equals(ok), rs.getString("last_test_message"));
        return new Stored(rs.getString("host"), rs.getInt("port"), parseSecurity(rs.getString("security")), rs.getString("username"),
                rs.getString("password_ciphertext"), rs.getString("from_address"), rs.getString("from_name"), rs.getBoolean("notifications"),
                rs.getInt("version_no"), rs.getTimestamp("updated_at").toInstant(), test);
    }

    private DeliveryView delivery() {
        Instant now = clock.now();
        long sent = count("SELECT COUNT(*) FROM notification_emails WHERE status='SENT' AND sent_at>=?", now.minus(Duration.ofDays(1)));
        long pending = count("SELECT COUNT(*) FROM notification_emails WHERE status='PENDING'");
        long failed = count("SELECT COUNT(*) FROM notification_emails WHERE status='FAILED' AND created_at>=?", now.minus(Duration.ofDays(7)));
        List<RecentEmail> recent = jdbc.query("SELECT id,type,to_address,subject,status,attempts,last_error,created_at,sent_at FROM notification_emails ORDER BY created_at DESC LIMIT 20",
                (rs, n) -> new RecentEmail(rs.getString("id"), rs.getString("type"), mask(rs.getString("to_address")), rs.getString("subject"),
                        rs.getString("status"), rs.getInt("attempts"), rs.getString("last_error"),
                        rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("sent_at") == null ? null : rs.getTimestamp("sent_at").toInstant()));
        return new DeliveryView(sent, pending, failed, recent);
    }

    private long count(String sql, Object... args) {
        Long value = jdbc.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }

    private static JavaMailSenderImpl sender(Stored stored, String password) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(stored.host());
        sender.setPort(stored.port());
        sender.setUsername(stored.username());
        sender.setPassword(password);
        sender.setDefaultEncoding(StandardCharsets.UTF_8.name());
        Properties properties = sender.getJavaMailProperties();
        String timeout = String.valueOf(TIMEOUT.toMillis());
        properties.put("mail.smtp.auth", "true");
        properties.put("mail.smtp.connectiontimeout", timeout);
        properties.put("mail.smtp.timeout", timeout);
        properties.put("mail.smtp.writetimeout", timeout);
        switch (stored.security()) {
            case SSL -> properties.put("mail.smtp.ssl.enable", "true");
            case STARTTLS -> {
                properties.put("mail.smtp.starttls.enable", "true");
                properties.put("mail.smtp.starttls.required", "true");
            }
            case NONE -> { }
        }
        return sender;
    }

    /** What went wrong, in words an administrator can act on. */
    static String explain(Throwable failure) {
        if (failure instanceof MailAuthenticationException) return "账号或授权码不正确：邮件服务器拒绝登录";
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof jakarta.mail.AuthenticationFailedException) return "账号或授权码不正确：邮件服务器拒绝登录";
            if (cause instanceof UnknownHostException) return "找不到这个 SMTP 服务器，请检查服务器地址";
            if (cause instanceof SocketTimeoutException) return "连接邮件服务器超时，请检查端口和加密方式，或服务器的出站网络";
            if (cause instanceof ConnectException) return "连接不上邮件服务器，请检查服务器地址和端口";
            if (cause instanceof javax.net.ssl.SSLException) return "加密连接失败，请检查加密方式（465 端口一般选 SSL，587 选 STARTTLS）";
            if (cause instanceof jakarta.mail.SendFailedException) return "邮件服务器拒收：收件地址或发件地址被拒绝";
        }
        String message = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
        return clip("发送失败：" + message.replaceAll("[\\r\\n]+", " "), 200);
    }

    /** QQ mail and Tencent Exmail only send as the signed-in address. */
    static boolean requiresMatchingSender(String host) {
        String value = host == null ? "" : host.toLowerCase(Locale.ROOT);
        return value.endsWith("qq.com");
    }

    static String mask(String address) {
        if (address == null || address.isBlank()) return "";
        int at = address.indexOf('@');
        if (at <= 0) return address.length() <= 2 ? "***" : address.substring(0, 2) + "***";
        String name = address.substring(0, at);
        return (name.length() <= 2 ? name.substring(0, 1) : name.substring(0, 2)) + "***" + address.substring(at);
    }

    private static MailSecurity parseSecurity(String value) {
        try {
            return MailSecurity.valueOf(trim(value).toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException invalid) {
            throw AppException.user("MAIL_SECURITY_INVALID", "加密方式只能是 SSL、STARTTLS 或不加密");
        }
    }

    private static void assertAdmin(CurrentAccount current) {
        if (current == null || !"ADMIN".equals(current.role())) {
            throw AppException.forbidden("ADMIN_REQUIRED", "只有管理员可以修改邮件设置");
        }
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static String clip(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max - 1) + "…";
    }

    private record Built(int version, JavaMailSender sender) {
    }

    private record Environment(String host, String username, boolean passwordSet, String from, String fromName, boolean notifications) {
    }

    private record Stored(String host, int port, MailSecurity security, String username, String passwordCiphertext, String fromAddress,
            String fromName, boolean notifications, int version, Instant updatedAt, TestResult lastTest) {
    }

    public record SettingsCommand(String host, Integer port, String security, String username, String password, String fromAddress,
            String fromName, Boolean notifications, Integer expectedVersion) {
    }

    /**
     * @param source       ADMIN (saved here), ENVIRONMENT (from the deployment) or NONE
     * @param notifications whether notification emails go out right now
     */
    public record SettingsView(String source, boolean notifications, boolean canStoreSecrets, AdminView admin,
            EnvironmentView environment, TestResult lastTest, DeliveryView delivery) {
    }

    /** The saved settings; the password is never included. {@code usable} is false when it cannot be decrypted. */
    public record AdminView(String host, int port, String security, String username, String fromAddress, String fromName,
            boolean notifications, int version, Instant updatedAt, boolean usable) {
    }

    public record EnvironmentView(boolean configured, String host, String username, String fromAddress, boolean notifications) {
    }

    public record TestResult(Instant at, boolean ok, String message) {
    }

    public record DeliveryView(long sent24h, long pending, long failed7d, List<RecentEmail> recent) {
    }

    public record RecentEmail(String id, String type, String to, String subject, String status, int attempts, String lastError,
            Instant createdAt, Instant sentAt) {
    }
}
