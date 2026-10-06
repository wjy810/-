package com.jobproof.modules.identity.application;

import com.jobproof.infrastructure.config.DevAdminProperties;
import com.jobproof.infrastructure.config.DevSeekerProperties;
import com.jobproof.infrastructure.config.JobProofProperties;
import com.jobproof.infrastructure.mail.DevMailMessage;
import com.jobproof.infrastructure.mail.DevMailbox;
import com.jobproof.modules.audit.application.AuditService;
import com.jobproof.modules.identity.domain.AccountRules;
import com.jobproof.modules.identity.domain.AccountStatus;
import com.jobproof.modules.identity.domain.PasswordResetPolicy;
import com.jobproof.modules.identity.domain.VerificationChannel;
import com.jobproof.modules.identity.domain.VerificationPurpose;
import com.jobproof.modules.identity.infra.AccountEntity;
import com.jobproof.modules.identity.infra.AccountJpaRepository;
import com.jobproof.modules.identity.infra.PasswordResetEntity;
import com.jobproof.modules.identity.infra.PasswordResetJpaRepository;
import com.jobproof.modules.identity.infra.SessionEntity;
import com.jobproof.modules.identity.infra.SessionJpaRepository;
import com.jobproof.shared.auth.CurrentAccount;
import com.jobproof.shared.error.AppException;
import com.jobproof.shared.id.Ids;
import com.jobproof.shared.security.Tokens;
import com.jobproof.shared.time.ClockPort;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdentityService {

    private final AccountJpaRepository accounts;
    private final SessionJpaRepository sessions;
    private final PasswordResetJpaRepository resets;
    private final PasswordEncoder passwordEncoder;
    private final ClockPort clock;
    private final AuditService auditService;
    private final DevMailbox mailbox;
    private final DevAdminProperties devAdmin;
    private final DevSeekerProperties devSeeker;
    private final Duration sessionTtl;
    private final Duration resetTtl;
    private final boolean mailboxEnabled;
    private final ContactVerificationService verificationService;
    private final JobProofProperties properties;
    private final LoginRateLimitService loginRateLimit;
    private static final String DUMMY_PASSWORD_HASH = "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    @Autowired
    public IdentityService(
            AccountJpaRepository accounts,
            SessionJpaRepository sessions,
            PasswordResetJpaRepository resets,
            PasswordEncoder passwordEncoder,
            ClockPort clock,
            AuditService auditService,
            DevMailbox mailbox,
            DevAdminProperties devAdmin,
            DevSeekerProperties devSeeker,
            @Value("${jobproof.session.ttl-days:14}") long sessionTtlDays,
            @Value("${jobproof.reset.ttl-minutes:10}") long resetTtlMinutes,
            @Value("${jobproof.dev.mailbox-enabled:false}") boolean mailboxEnabled,
            ContactVerificationService verificationService,
            JobProofProperties properties,
            LoginRateLimitService loginRateLimit) {
        this.accounts = accounts;
        this.sessions = sessions;
        this.resets = resets;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.auditService = auditService;
        this.mailbox = mailbox;
        this.devAdmin = devAdmin;
        this.devSeeker = devSeeker;
        this.sessionTtl = Duration.ofDays(sessionTtlDays);
        this.resetTtl = Duration.ofMinutes(resetTtlMinutes);
        this.mailboxEnabled = mailboxEnabled;
        this.verificationService = verificationService;
        this.properties = properties;
        this.loginRateLimit = loginRateLimit;
    }

    public IdentityService(
            AccountJpaRepository accounts,
            SessionJpaRepository sessions,
            PasswordResetJpaRepository resets,
            PasswordEncoder passwordEncoder,
            ClockPort clock,
            AuditService auditService,
            DevMailbox mailbox,
            DevAdminProperties devAdmin,
            long sessionTtlDays,
            long resetTtlMinutes,
            boolean mailboxEnabled) {
        this(accounts, sessions, resets, passwordEncoder, clock, auditService, mailbox, devAdmin,
                new DevSeekerProperties(), sessionTtlDays, resetTtlMinutes, mailboxEnabled, null, new JobProofProperties(), localLoginLimiter(clock));
    }

    private static LoginRateLimitService localLoginLimiter(ClockPort clock) {
        var settings = new com.jobproof.infrastructure.config.LoginRateLimitProperties();
        settings.setMode("local");
        return new LoginRateLimitService(settings,
                new org.springframework.beans.factory.support.StaticListableBeanFactory()
                        .getBeanProvider(org.springframework.data.redis.core.StringRedisTemplate.class), clock);
    }

    @Transactional
    public LoginResult register(
            VerificationChannel channel,
            String destination,
            String verificationToken,
            String password,
            boolean acceptedTerms,
            boolean acceptedPrivacy) {
        if (!properties.getPolicies().isRegistrationEnabled()) {
            throw AppException.dependency("REGISTRATION_UNAVAILABLE", "账号注册暂未开放");
        }
        if (!acceptedTerms || !acceptedPrivacy) {
            throw AppException.user("AGREEMENT_REQUIRED", "请先阅读并同意用户协议和隐私政策");
        }
        if (verificationService == null) {
            throw AppException.dependency("VERIFICATION_PROVIDER_UNAVAILABLE", "身份验证服务暂时不可用");
        }
        var verified = verificationService.consumeVerifiedToken(
                verificationToken, channel, destination, VerificationPurpose.REGISTER);
        AccountRules.validatePassword(password, verified.destination());
        if (contactExists(channel, verified.destination())) throw contactAlreadyRegistered();

        Instant now = clock.now();
        AccountEntity account = new AccountEntity();
        account.setId(Ids.newId());
        if (channel == VerificationChannel.EMAIL) {
            account.setEmail(verified.destination());
            account.setEmailVerifiedAt(verified.verifiedAt());
        } else {
            account.setPhoneE164(verified.destination());
            account.setPhoneVerifiedAt(verified.verifiedAt());
        }
        account.setPrimaryChannel(channel.name());
        account.setTermsVersion(properties.getPolicies().getTermsVersion());
        account.setTermsAcceptedAt(now);
        account.setPrivacyVersion(properties.getPolicies().getPrivacyVersion());
        account.setPrivacyAcceptedAt(now);
        account.setPasswordHash(passwordEncoder.encode(password));
        account.setStatus(AccountStatus.ACTIVE.name());
        account.setRole("SEEKER");
        account.setCreatedAt(now);
        account.setUpdatedAt(now);
        account.setPasswordChangedAt(now);
        try {
            accounts.saveAndFlush(account);
        } catch (DataIntegrityViolationException exception) {
            if (isAccountsContactUniqueViolation(exception)) throw contactAlreadyRegistered();
            throw exception;
        }
        auditService.append(account.getId(), "ACCOUNT_REGISTERED", "ACCOUNT", account.getId(),
                channel == VerificationChannel.EMAIL ? "邮箱验证注册成功" : "手机号验证注册成功");
        return createSession(account, now, "注册后建立登录会话");
    }

    @Transactional
    public AccountView register(String rawEmail, String password) {
        String email = AccountRules.normalizeEmail(rawEmail);
        AccountRules.validatePassword(password, email);
        if (accounts.existsByEmail(email)) {
            throw emailAlreadyRegistered();
        }
        Instant now = clock.now();
        AccountEntity account = new AccountEntity();
        account.setId(Ids.newId());
        account.setEmail(email);
        account.setEmailVerifiedAt(now);
        account.setPrimaryChannel(VerificationChannel.EMAIL.name());
        account.setPasswordHash(passwordEncoder.encode(password));
        account.setStatus(AccountStatus.ACTIVE.name());
        account.setRole("SEEKER");
        account.setCreatedAt(now);
        account.setUpdatedAt(now);
        account.setPasswordChangedAt(now);
        try {
            accounts.saveAndFlush(account);
        } catch (DataIntegrityViolationException ex) {
            if (isAccountsEmailUniqueViolation(ex)) {
                throw emailAlreadyRegistered();
            }
            throw ex;
        }
        auditService.append(account.getId(), "ACCOUNT_REGISTERED", "ACCOUNT", account.getId(), "邮箱注册成功");
        return toView(account);
    }

    @Transactional(noRollbackFor = AppException.class)
    public LoginResult login(String rawIdentifier, String password) {
        return login(rawIdentifier, password, "internal");
    }

    @Transactional(noRollbackFor = AppException.class)
    public LoginResult login(String rawIdentifier, String password, String clientIp) {
        String key = loginKey(rawIdentifier);
        // Acquire before account lookup and BCrypt, including unknown/disabled accounts.
        loginRateLimit.checkAndRecord(key, clientIp);
        if (rawIdentifier == null || rawIdentifier.length() > 320
                || password == null || password.isEmpty() || password.length() > 72
                || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
            throw failedLogin(key, clientIp);
        }
        boolean phoneLogin = isPhoneIdentifier(rawIdentifier);
        AccountEntity account;
        try { account = resolveLoginAccount(rawIdentifier); }
        catch (AppException invalidIdentifier) { account = null; }
        boolean passwordMatches = passwordEncoder.matches(password,
                account == null ? DUMMY_PASSWORD_HASH : account.getPasswordHash());
        if (account == null || !passwordMatches || !AccountStatus.ACTIVE.name().equals(account.getStatus())) {
            throw failedLogin(key, clientIp);
        }
        Instant now = clock.now();
        String auditMessage = phoneLogin ? "手机号登录成功"
                : devAdmin.matchesAlias(rawIdentifier) || devSeeker.matchesAlias(rawIdentifier)
                        ? "账号登录成功" : "邮箱登录成功";
        return createSession(account, now, auditMessage);
    }

    private AppException failedLogin(String key, String ip) {
        auditService.append(null, "ACCOUNT_LOGIN_FAILED", "LOGIN_ATTEMPT", Tokens.sha256(key),
                "登录验证失败; client=" + Tokens.sha256(ip == null ? "unknown" : ip));
        return AppException.user("INVALID_CREDENTIALS", "邮箱、手机号或密码不正确");
    }

    private String loginKey(String rawIdentifier) {
        String candidate = rawIdentifier == null ? "" : rawIdentifier.trim();
        if (candidate.length() > 320) candidate = candidate.substring(0, 320);
        if (devAdmin.matchesAlias(candidate)) return AccountRules.normalizeEmail(devAdmin.getEmail());
        if (devSeeker.matchesAlias(candidate)) return AccountRules.normalizeEmail(devSeeker.getEmail());
        try {
            return isPhoneIdentifier(candidate) ? AccountRules.normalizePhoneE164(candidate) : AccountRules.normalizeEmail(candidate);
        } catch (AppException invalidIdentifier) { return candidate.toLowerCase(Locale.ROOT); }
    }

    private static boolean isPhoneIdentifier(String value) {
        return value != null && value.trim().matches("[+0-9\\s()\\-]+");
    }

    private AccountEntity resolveLoginAccount(String rawLogin) {
        if (devAdmin.matchesAlias(rawLogin)) {
            return accounts.findByEmail(AccountRules.normalizeEmail(devAdmin.getEmail())).orElse(null);
        }
        if (devSeeker.matchesAlias(rawLogin)) {
            return accounts.findByEmail(AccountRules.normalizeEmail(devSeeker.getEmail())).orElse(null);
        }
        String candidate = rawLogin == null ? "" : rawLogin.trim();
        if (isPhoneIdentifier(candidate)) {
            return accounts.findByPhoneE164(AccountRules.normalizePhoneE164(candidate)).orElse(null);
        }
        return accounts.findByEmail(AccountRules.normalizeEmail(rawLogin)).orElse(null);
    }

    @Transactional
    public void logout(CurrentAccount current) {
        Instant now = clock.now();
        SessionEntity session = sessions.findById(current.sessionId()).orElseThrow(AppException::unauthenticated);
        if (session.getRevokedAt() == null) {
            session.setRevokedAt(now);
            sessions.save(session);
        }
        auditService.append(current.accountId(), "ACCOUNT_LOGOUT", "SESSION", current.sessionId(), "退出当前会话");
    }

    @Transactional
    public void changePassword(CurrentAccount current, String currentPassword, String newPassword) {
        if (currentPassword == null || currentPassword.length() > 72
                || currentPassword.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
            throw AppException.user("INVALID_CREDENTIALS", "当前密码不正确");
        }
        AccountEntity account = accounts.findById(current.accountId()).orElseThrow(AppException::unauthenticated);
        if (!passwordEncoder.matches(currentPassword, account.getPasswordHash())) {
            throw AppException.user("INVALID_CREDENTIALS", "当前密码不正确");
        }
        AccountRules.validatePassword(newPassword, account.getEmail());
        Instant now = clock.now();
        replacePasswordAndRevokeSessions(account, newPassword, now);
        auditService.append(account.getId(), "PASSWORD_CHANGED", "ACCOUNT", account.getId(), "登录态改密，全部会话已失效");
    }

    @Transactional
    public void requestPasswordReset(String rawEmail) {
        String email = AccountRules.normalizeEmail(rawEmail);
        String code = Tokens.sixDigitCode();
        String codeHash = Tokens.sha256(code);
        Optional<AccountEntity> found = accounts.findByEmail(email);
        if (found.isEmpty()) {
            return;
        }
        AccountEntity account = found.get();
        Instant now = clock.now();
        resets.consumeAllOpenByAccountId(account.getId(), now);
        PasswordResetEntity challenge = new PasswordResetEntity();
        challenge.setId(Ids.newId());
        challenge.setAccountId(account.getId());
        challenge.setCodeHash(codeHash);
        challenge.setExpiresAt(now.plus(resetTtl));
        challenge.setFailedAttempts(0);
        challenge.setCreatedAt(now);
        resets.save(challenge);
        if (mailboxEnabled) {
            mailbox.deliver(new DevMailMessage(email, "重置密码验证码", "PASSWORD_RESET", code));
        }
        auditService.append(account.getId(), "PASSWORD_RESET_REQUESTED", "ACCOUNT", account.getId(), "已发送邮箱验证码");
    }

    @Transactional(noRollbackFor = AppException.class)
    public void confirmPasswordReset(String rawEmail, String code, String newPassword) {
        String email = AccountRules.normalizeEmail(rawEmail);
        AccountRules.validatePassword(newPassword, email);
        AccountEntity account = accounts.findByEmail(email).orElseThrow(PasswordResetPolicy::invalid);
        Instant now = clock.now();
        PasswordResetEntity challenge = resets.findFirstByAccountIdAndConsumedAtIsNullOrderByCreatedAtDesc(account.getId())
                .orElseThrow(PasswordResetPolicy::invalid);
        if (PasswordResetPolicy.expired(challenge.getExpiresAt(), now)
                || PasswordResetPolicy.locked(challenge.getFailedAttempts())) {
            challenge.setConsumedAt(now);
            resets.save(challenge);
            throw PasswordResetPolicy.invalid();
        }
        if (!PasswordResetPolicy.matches(challenge.getCodeHash(), code)) {
            challenge.setFailedAttempts(challenge.getFailedAttempts() + 1);
            if (PasswordResetPolicy.locked(challenge.getFailedAttempts())) {
                challenge.setConsumedAt(now);
            }
            resets.save(challenge);
            throw PasswordResetPolicy.invalid();
        }
        resets.consumeAllOpenByAccountId(account.getId(), now);
        replacePasswordAndRevokeSessions(account, newPassword, now);
        auditService.append(account.getId(), "PASSWORD_RESET_CONFIRMED", "ACCOUNT", account.getId(), "验证码改密，全部会话已失效");
    }

    @Transactional
    public void confirmPasswordReset(
            VerificationChannel channel,
            String destination,
            String verificationToken,
            String newPassword) {
        if (verificationService == null) {
            throw AppException.dependency("VERIFICATION_PROVIDER_UNAVAILABLE", "身份验证服务暂时不可用");
        }
        var verified = verificationService.consumeVerifiedToken(
                verificationToken, channel, destination, VerificationPurpose.LOGIN_RECOVERY);
        AccountRules.validatePassword(newPassword, verified.destination());
        AccountEntity account = findByContact(channel, verified.destination()).orElse(null);
        if (account == null) {
            return;
        }
        Instant now = clock.now();
        replacePasswordAndRevokeSessions(account, newPassword, now);
        auditService.append(account.getId(), "PASSWORD_RESET_CONFIRMED", "ACCOUNT", account.getId(),
                "验证凭据改密，全部会话已失效");
    }

    @Transactional(readOnly = true)
    public AccountView current(CurrentAccount current) {
        AccountEntity account = accounts.findById(current.accountId()).orElseThrow(AppException::unauthenticated);
        return toView(account);
    }

    @Transactional(readOnly = true)
    public List<SessionView> listSessions(CurrentAccount current) {
        Instant now = clock.now();
        return sessions.findByAccountId(current.accountId()).stream()
                .filter(session -> session.getRevokedAt() == null && session.getExpiresAt().isAfter(now))
                .sorted(Comparator.comparing(SessionEntity::getLastSeenAt).reversed())
                .map(session -> new SessionView(
                        session.getId(),
                        session.getId().equals(current.sessionId()),
                        session.getCreatedAt(),
                        session.getLastSeenAt(),
                        session.getExpiresAt()))
                .toList();
    }

    @Transactional
    public void revokeSession(CurrentAccount current, String sessionId) {
        SessionEntity session = sessions.findById(sessionId)
                .orElseThrow(() -> AppException.user("SESSION_NOT_FOUND", "会话不存在"));
        if (!session.getAccountId().equals(current.accountId())) {
            throw AppException.forbidden("OBJECT_FORBIDDEN", "不能管理他人的会话");
        }
        if (session.getId().equals(current.sessionId())) {
            throw AppException.conflict("CURRENT_SESSION_PROTECTED", "当前会话请使用退出登录结束");
        }
        if (session.getRevokedAt() == null) {
            session.setRevokedAt(clock.now());
            sessions.save(session);
            auditService.append(current.accountId(), "SESSION_REVOKED", "SESSION", sessionId, "已退出其他登录设备");
        }
    }

    @Transactional
    public int revokeOtherSessions(CurrentAccount current) {
        Instant now = clock.now();
        int revoked = 0;
        for (SessionEntity session : sessions.findByAccountId(current.accountId())) {
            if (!session.getId().equals(current.sessionId()) && session.getRevokedAt() == null && session.getExpiresAt().isAfter(now)) {
                session.setRevokedAt(now);
                sessions.save(session);
                revoked++;
            }
        }
        if (revoked > 0) {
            auditService.append(current.accountId(), "OTHER_SESSIONS_REVOKED", "ACCOUNT", current.accountId(), "已退出其他登录设备");
        }
        return revoked;
    }

    @Transactional
    public Optional<CurrentAccount> authenticate(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }
        Instant now = clock.now();
        return sessions.findByTokenHash(Tokens.sha256(rawToken))
                .filter(session -> session.getRevokedAt() == null && session.getExpiresAt().isAfter(now))
                .flatMap(session -> accounts.findById(session.getAccountId())
                        .filter(account -> AccountStatus.ACTIVE.name().equals(account.getStatus())
                        || AccountStatus.DELETION_PENDING.name().equals(account.getStatus()))
                        .map(account -> {
                            session.setLastSeenAt(now);
                            sessions.save(session);
                            return new CurrentAccount(account.getId(), account.getEmail(), account.getRole(), session.getId());
                        }));
    }

    @Transactional
    public void markDeletionPending(String accountId) {
        AccountEntity account = accounts.findById(accountId)
                .orElseThrow(() -> AppException.user("ACCOUNT_NOT_FOUND", "账号不存在"));
        Instant now = clock.now();
        account.setStatus(AccountStatus.DELETION_PENDING.name());
        account.setUpdatedAt(now);
        accounts.save(account);
    }

    private void replacePasswordAndRevokeSessions(AccountEntity account, String newPassword, Instant now) {
        account.setPasswordHash(passwordEncoder.encode(newPassword));
        account.setPasswordChangedAt(now);
        account.setUpdatedAt(now);
        accounts.save(account);
        sessions.revokeAllByAccountId(account.getId(), now);
    }

    private LoginResult createSession(AccountEntity account, Instant now, String auditMessage) {
        String rawToken = Tokens.randomToken();
        SessionEntity session = new SessionEntity();
        session.setId(Ids.newId());
        session.setAccountId(account.getId());
        session.setTokenHash(Tokens.sha256(rawToken));
        session.setExpiresAt(now.plus(sessionTtl));
        session.setCreatedAt(now);
        session.setLastSeenAt(now);
        sessions.save(session);
        auditService.append(account.getId(), "ACCOUNT_LOGIN", "SESSION", session.getId(), auditMessage);
        return new LoginResult(toView(account), rawToken, session.getExpiresAt());
    }

    private boolean contactExists(VerificationChannel channel, String destination) {
        return channel == VerificationChannel.EMAIL
                ? accounts.existsByEmail(destination)
                : accounts.existsByPhoneE164(destination);
    }

    private Optional<AccountEntity> findByContact(VerificationChannel channel, String destination) {
        return channel == VerificationChannel.EMAIL
                ? accounts.findByEmail(destination)
                : accounts.findByPhoneE164(destination);
    }

    private static AccountView toView(AccountEntity account) {
        String phoneMasked = maskPhone(account.getPhoneE164());
        String displayIdentifier = account.getEmail() != null ? account.getEmail() : phoneMasked;
        return new AccountView(
                account.getId(),
                account.getEmail(),
                phoneMasked,
                displayIdentifier,
                account.getPrimaryChannel(),
                account.getStatus(),
                account.getRole(),
                account.getCreatedAt());
    }

    private static AppException emailAlreadyRegistered() {
        return AppException.conflict("EMAIL_ALREADY_REGISTERED", "该邮箱已注册");
    }

    private static AppException contactAlreadyRegistered() {
        return AppException.conflict("CONTACT_ALREADY_REGISTERED", "该联系方式已注册，请直接登录或找回密码");
    }

    /** 仅认库约束 uk_accounts_email，其它完整性冲突原样抛出，避免一律伪装成 409。 */
    static boolean isAccountsEmailUniqueViolation(DataIntegrityViolationException ex) {
        for (Throwable current = ex; current != null; current = current.getCause()) {
            if (current instanceof ConstraintViolationException constraint
                    && mentionsAccountsEmailConstraint(constraint.getConstraintName())) {
                return true;
            }
            if (mentionsAccountsEmailConstraint(current.getMessage())) {
                return true;
            }
        }
        return false;
    }

    static boolean isAccountsContactUniqueViolation(DataIntegrityViolationException ex) {
        if (isAccountsEmailUniqueViolation(ex)) return true;
        for (Throwable current = ex; current != null; current = current.getCause()) {
            String text = current.getMessage();
            if (text != null && text.toLowerCase(Locale.ROOT).contains("uk_accounts_phone")) return true;
            if (current instanceof ConstraintViolationException constraint) {
                String name = constraint.getConstraintName();
                if (name != null && name.toLowerCase(Locale.ROOT).contains("uk_accounts_phone")) return true;
            }
        }
        return false;
    }

    private static boolean mentionsAccountsEmailConstraint(String text) {
        return text != null && text.toLowerCase(Locale.ROOT).contains("uk_accounts_email");
    }

    private static String maskPhone(String phoneE164) {
        if (phoneE164 == null || phoneE164.length() != 14 || !phoneE164.startsWith("+86")) return null;
        String local = phoneE164.substring(3);
        return "+86 " + local.substring(0, 3) + "****" + local.substring(7);
    }

    public record AccountView(
            String id,
            String email,
            String phoneMasked,
            String displayIdentifier,
            String primaryChannel,
            String status,
            String role,
            Instant createdAt) {
    }

    public record SessionView(String id, boolean current, Instant createdAt, Instant lastSeenAt, Instant expiresAt) {
    }

    public record LoginResult(AccountView account, String rawSessionToken, Instant expiresAt) {
    }
}
