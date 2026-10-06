package com.jobproof.modules.identity.application;

import com.jobproof.infrastructure.config.DevSeekerProperties;
import com.jobproof.modules.audit.application.AuditService;
import com.jobproof.modules.identity.domain.AccountRules;
import com.jobproof.modules.identity.domain.AccountStatus;
import com.jobproof.modules.identity.infra.AccountEntity;
import com.jobproof.modules.identity.infra.AccountJpaRepository;
import com.jobproof.modules.identity.infra.SessionJpaRepository;
import com.jobproof.shared.id.Ids;
import com.jobproof.shared.time.ClockPort;
import java.time.Instant;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name = "jobproof.dev.seeker.enabled", havingValue = "true")
public class DevSeekerAccountInitializer implements ApplicationRunner {

    private final AccountJpaRepository accounts;
    private final SessionJpaRepository sessions;
    private final PasswordEncoder passwordEncoder;
    private final ClockPort clock;
    private final AuditService audit;
    private final DevSeekerProperties properties;

    public DevSeekerAccountInitializer(AccountJpaRepository accounts, SessionJpaRepository sessions,
            PasswordEncoder passwordEncoder, ClockPort clock, AuditService audit,
            DevSeekerProperties properties) {
        this.accounts = accounts;
        this.sessions = sessions;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.audit = audit;
        this.properties = properties;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        String email = AccountRules.normalizeEmail(properties.getEmail());
        String password = properties.getPassword();
        if (password == null || password.isBlank() || password.length() > 72) {
            throw new IllegalStateException("jobproof.dev.seeker.password must contain 1 to 72 characters");
        }
        Instant now = clock.now();
        AccountEntity account = accounts.findByEmail(email).orElseGet(() -> newAccount(email, now));
        account.setEmail(email);
        account.setEmailVerifiedAt(now);
        account.setPrimaryChannel("EMAIL");
        account.setPasswordHash(passwordEncoder.encode(password));
        account.setStatus(AccountStatus.ACTIVE.name());
        account.setRole("SEEKER");
        account.setPasswordChangedAt(now);
        account.setUpdatedAt(now);
        accounts.saveAndFlush(account);
        sessions.revokeAllByAccountId(account.getId(), now);
        audit.append(account.getId(), "DEV_SEEKER_RESET", "ACCOUNT", account.getId(),
                "本地开发求职者凭据已恢复，旧会话已失效");
    }

    private static AccountEntity newAccount(String email, Instant now) {
        AccountEntity account = new AccountEntity();
        account.setId(Ids.newId());
        account.setEmail(email);
        account.setCreatedAt(now);
        return account;
    }
}
