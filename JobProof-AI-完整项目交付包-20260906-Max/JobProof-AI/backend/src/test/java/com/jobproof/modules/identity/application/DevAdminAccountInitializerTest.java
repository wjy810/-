package com.jobproof.modules.identity.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jobproof.infrastructure.config.DevAdminProperties;
import com.jobproof.modules.audit.application.AuditService;
import com.jobproof.modules.identity.infra.AccountEntity;
import com.jobproof.modules.identity.infra.AccountJpaRepository;
import com.jobproof.modules.identity.infra.SessionJpaRepository;
import com.jobproof.shared.time.ClockPort;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class DevAdminAccountInitializerTest {

    @Mock
    AccountJpaRepository accounts;
    @Mock
    SessionJpaRepository sessions;
    @Mock
    PasswordEncoder passwordEncoder;
    @Mock
    AuditService auditService;

    private final Instant now = Instant.parse("2026-08-23T00:00:00Z");
    private DevAdminAccountInitializer initializer;

    @BeforeEach
    void setUp() {
        DevAdminProperties properties = new DevAdminProperties();
        properties.setEnabled(true);
        properties.setAlias("admin");
        properties.setEmail("admin@jobproof.local");
        properties.setPassword("admin");
        ClockPort clock = () -> now;
        initializer = new DevAdminAccountInitializer(
                accounts, sessions, passwordEncoder, clock, auditService, properties);
    }

    @Test
    void existingDevelopmentAccountIsRepairedAndOldSessionsAreRevoked() {
        Instant createdAt = Instant.parse("2026-08-01T00:00:00Z");
        AccountEntity account = new AccountEntity();
        account.setId("admin-account");
        account.setEmail("admin@jobproof.local");
        account.setPasswordHash("old-hash");
        account.setStatus("DELETION_PENDING");
        account.setRole("SEEKER");
        account.setCreatedAt(createdAt);
        account.setUpdatedAt(createdAt);
        account.setPasswordChangedAt(createdAt);
        when(accounts.findByEmail("admin@jobproof.local")).thenReturn(Optional.of(account));
        when(passwordEncoder.encode("admin")).thenReturn("new-hash");

        initializer.run(null);

        assertEquals("admin-account", account.getId());
        assertEquals(createdAt, account.getCreatedAt());
        assertEquals("new-hash", account.getPasswordHash());
        assertEquals("ACTIVE", account.getStatus());
        assertEquals("ADMIN", account.getRole());
        assertEquals(now, account.getPasswordChangedAt());
        assertEquals(now, account.getUpdatedAt());
        verify(accounts).saveAndFlush(account);
        verify(sessions).revokeAllByAccountId("admin-account", now);
        verify(auditService).append("admin-account", "DEV_ADMIN_RESET", "ACCOUNT", "admin-account",
                "本地开发管理员凭据已恢复，旧会话已失效");
    }
}
