package com.jobproof.modules.identity.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jobproof.infrastructure.config.DevAdminProperties;
import com.jobproof.infrastructure.mail.DevMailbox;
import com.jobproof.modules.audit.application.AuditService;
import com.jobproof.modules.identity.infra.AccountEntity;
import com.jobproof.modules.identity.infra.AccountJpaRepository;
import com.jobproof.modules.identity.infra.PasswordResetEntity;
import com.jobproof.modules.identity.infra.PasswordResetJpaRepository;
import com.jobproof.modules.identity.infra.SessionJpaRepository;
import com.jobproof.shared.error.AppException;
import com.jobproof.shared.security.Tokens;
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
class IdentityServicePasswordResetTest {

    @Mock
    AccountJpaRepository accounts;
    @Mock
    SessionJpaRepository sessions;
    @Mock
    PasswordResetJpaRepository resets;
    @Mock
    PasswordEncoder passwordEncoder;
    @Mock
    AuditService auditService;
    @Mock
    DevMailbox mailbox;

    private final MutableClock clock = new MutableClock(Instant.parse("2026-08-19T00:00:00Z"));
    private IdentityService service;

    @BeforeEach
    void setUp() {
        service = newService(true);
    }

    @Test
    void unknownEmailDoesNotSaveChallengeOrDeliverMail() {
        when(accounts.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        service.requestPasswordReset("nobody@example.com");

        verify(resets, never()).save(any());
        verify(resets, never()).consumeAllOpenByAccountId(any(), any());
        verify(mailbox, never()).deliver(any());
        verify(auditService, never()).append(any(), any(), any(), any(), any());
    }

    @Test
    void mailboxDisabledDoesNotDeliverPlaintextCode() {
        service = newService(false);
        AccountEntity account = account("acc-1", "seeker@example.com");
        when(accounts.findByEmail("seeker@example.com")).thenReturn(Optional.of(account));

        service.requestPasswordReset("seeker@example.com");

        verify(resets).consumeAllOpenByAccountId(eq("acc-1"), eq(clock.now()));
        verify(resets).save(any(PasswordResetEntity.class));
        verify(mailbox, never()).deliver(any());
    }

    @Test
    void weakNewPasswordDoesNotTouchChallengeOrSessions() {
        AppException ex = assertThrows(AppException.class,
                () -> service.confirmPasswordReset("seeker@example.com", "123456", "short"));
        assertEquals("PASSWORD_TOO_WEAK", ex.reason());
        verify(accounts, never()).findByEmail(any());
        verify(resets, never()).findFirstByAccountIdAndConsumedAtIsNullOrderByCreatedAtDesc(any());
        verify(sessions, never()).revokeAllByAccountId(any(), any());
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    void unknownEmailConfirmUsesSameInvalidCode() {
        when(accounts.findByEmail("nobody@example.com")).thenReturn(Optional.empty());
        AppException ex = assertThrows(AppException.class,
                () -> service.confirmPasswordReset("nobody@example.com", "123456", "Passw0rd2!"));
        assertEquals("RESET_CODE_INVALID", ex.reason());
        verify(resets, never()).findFirstByAccountIdAndConsumedAtIsNullOrderByCreatedAtDesc(any());
        verify(sessions, never()).revokeAllByAccountId(any(), any());
    }

    @Test
    void expiredCodeIsConsumedWithoutChangingPassword() {
        AccountEntity account = account("acc-1", "seeker@example.com");
        when(accounts.findByEmail("seeker@example.com")).thenReturn(Optional.of(account));
        PasswordResetEntity challenge = openChallenge("123456", clock.now().minusSeconds(1), 0);
        when(resets.findFirstByAccountIdAndConsumedAtIsNullOrderByCreatedAtDesc("acc-1"))
                .thenReturn(Optional.of(challenge));

        AppException ex = assertThrows(AppException.class,
                () -> service.confirmPasswordReset("seeker@example.com", "123456", "Passw0rd2!"));
        assertEquals("RESET_CODE_INVALID", ex.reason());
        assertNotNull(challenge.getConsumedAt());
        verify(resets).save(challenge);
        verify(sessions, never()).revokeAllByAccountId(any(), any());
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    void fifthWrongAttemptLocksEvenTheCorrectCode() {
        AccountEntity account = account("acc-1", "seeker@example.com");
        when(accounts.findByEmail("seeker@example.com")).thenReturn(Optional.of(account));
        PasswordResetEntity challenge = openChallenge("123456", clock.now().plusSeconds(600), 0);
        when(resets.findFirstByAccountIdAndConsumedAtIsNullOrderByCreatedAtDesc("acc-1"))
                .thenReturn(Optional.of(challenge));

        for (int i = 0; i < 5; i++) {
            AppException ex = assertThrows(AppException.class,
                    () -> service.confirmPasswordReset("seeker@example.com", "000000", "Passw0rd2!"));
            assertEquals("RESET_CODE_INVALID", ex.reason());
        }
        assertEquals(5, challenge.getFailedAttempts());
        assertNotNull(challenge.getConsumedAt());

        when(resets.findFirstByAccountIdAndConsumedAtIsNullOrderByCreatedAtDesc("acc-1"))
                .thenReturn(Optional.empty());
        AppException locked = assertThrows(AppException.class,
                () -> service.confirmPasswordReset("seeker@example.com", "123456", "Passw0rd2!"));
        assertEquals("RESET_CODE_INVALID", locked.reason());
        verify(sessions, never()).revokeAllByAccountId(any(), any());
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    void successConsumesAllChallengesAndRevokesSessions() {
        AccountEntity account = account("acc-1", "seeker@example.com");
        when(accounts.findByEmail("seeker@example.com")).thenReturn(Optional.of(account));
        PasswordResetEntity challenge = openChallenge("123456", clock.now().plusSeconds(600), 0);
        when(resets.findFirstByAccountIdAndConsumedAtIsNullOrderByCreatedAtDesc("acc-1"))
                .thenReturn(Optional.of(challenge));
        when(passwordEncoder.encode("Passw0rd3!")).thenReturn("new-hash");

        service.confirmPasswordReset("seeker@example.com", "123456", "Passw0rd3!");

        verify(resets).consumeAllOpenByAccountId("acc-1", clock.now());
        verify(sessions).revokeAllByAccountId("acc-1", clock.now());
        verify(accounts).save(account);
        verify(auditService).append("acc-1", "PASSWORD_RESET_CONFIRMED", "ACCOUNT", "acc-1", "验证码改密，全部会话已失效");
    }

    private IdentityService newService(boolean mailboxEnabled) {
        return new IdentityService(
                accounts,
                sessions,
                resets,
                passwordEncoder,
                clock,
                auditService,
                mailbox,
                new DevAdminProperties(),
                14,
                10,
                mailboxEnabled);
    }

    private static AccountEntity account(String id, String email) {
        AccountEntity entity = new AccountEntity();
        entity.setId(id);
        entity.setEmail(email);
        entity.setPasswordHash("old-hash");
        entity.setStatus("ACTIVE");
        entity.setRole("SEEKER");
        return entity;
    }

    private static PasswordResetEntity openChallenge(String code, Instant expiresAt, int failedAttempts) {
        PasswordResetEntity challenge = new PasswordResetEntity();
        challenge.setId("reset-1");
        challenge.setAccountId("acc-1");
        challenge.setCodeHash(Tokens.sha256(code));
        challenge.setExpiresAt(expiresAt);
        challenge.setFailedAttempts(failedAttempts);
        challenge.setCreatedAt(Instant.parse("2026-08-19T00:00:00Z"));
        return challenge;
    }

    private static final class MutableClock implements ClockPort {
        private Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        @Override
        public Instant now() {
            return now;
        }
    }
}
