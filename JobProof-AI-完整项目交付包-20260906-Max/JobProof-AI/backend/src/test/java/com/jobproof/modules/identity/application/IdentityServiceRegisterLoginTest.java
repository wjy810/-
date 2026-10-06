package com.jobproof.modules.identity.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jobproof.infrastructure.config.DevAdminProperties;
import com.jobproof.infrastructure.mail.DevMailbox;
import com.jobproof.modules.audit.application.AuditService;
import com.jobproof.modules.identity.infra.AccountEntity;
import com.jobproof.modules.identity.infra.AccountJpaRepository;
import com.jobproof.modules.identity.infra.PasswordResetJpaRepository;
import com.jobproof.modules.identity.infra.SessionJpaRepository;
import com.jobproof.shared.error.AppException;
import com.jobproof.shared.error.ErrorCategory;
import com.jobproof.shared.time.ClockPort;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class IdentityServiceRegisterLoginTest {

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
        service = new IdentityService(
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
                false);
    }

    @Test
    void duplicateEmailIsConflictAlreadyRegisteredNotSilent() {
        when(accounts.existsByEmail("seeker@example.com")).thenReturn(true);

        AppException ex = assertThrows(AppException.class, () -> service.register("Seeker@Example.com", "Passw0rd!"));

        assertEquals("EMAIL_ALREADY_REGISTERED", ex.reason());
        assertEquals(ErrorCategory.CONFLICT, ex.category());
        assertEquals("该邮箱已注册", ex.getMessage());
        verify(accounts, never()).save(any());
        verify(accounts, never()).saveAndFlush(any());
    }

    @Test
    void emailUniqueConstraintViolationIsConflictAlreadyRegistered() {
        when(accounts.existsByEmail("seeker@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Passw0rd!")).thenReturn("hash");
        when(accounts.saveAndFlush(any(AccountEntity.class))).thenThrow(emailUniqueViolation());

        AppException ex = assertThrows(AppException.class, () -> service.register("Seeker@Example.com", "Passw0rd!"));

        assertEquals("EMAIL_ALREADY_REGISTERED", ex.reason());
        assertEquals(ErrorCategory.CONFLICT, ex.category());
        assertEquals("该邮箱已注册", ex.getMessage());
        verify(auditService, never()).append(any(), any(), any(), any(), any());
    }

    @Test
    void otherIntegrityViolationIsNotRemappedToEmailAlreadyRegistered() {
        when(accounts.existsByEmail("seeker@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Passw0rd!")).thenReturn("hash");
        DataIntegrityViolationException other = otherUniqueViolation("uk_notification_dedup");
        when(accounts.saveAndFlush(any(AccountEntity.class))).thenThrow(other);

        DataIntegrityViolationException thrown = assertThrows(
                DataIntegrityViolationException.class,
                () -> service.register("seeker@example.com", "Passw0rd!"));

        assertEquals(other, thrown);
        verify(auditService, never()).append(any(), any(), any(), any(), any());
    }

    @Test
    void genericIntegrityViolationWithoutEmailConstraintIsNotRemapped() {
        when(accounts.existsByEmail("seeker@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Passw0rd!")).thenReturn("hash");
        DataIntegrityViolationException generic = new DataIntegrityViolationException(
                "could not execute statement; Column 'password_hash' cannot be null");
        when(accounts.saveAndFlush(any(AccountEntity.class))).thenThrow(generic);

        DataIntegrityViolationException thrown = assertThrows(
                DataIntegrityViolationException.class,
                () -> service.register("seeker@example.com", "Passw0rd!"));

        assertEquals(generic, thrown);
        assertFalse(IdentityService.isAccountsEmailUniqueViolation(generic));
    }

    @Test
    void onlyAccountsEmailConstraintNameIsRecognized() {
        assertTrue(IdentityService.isAccountsEmailUniqueViolation(emailUniqueViolation()));
        assertTrue(IdentityService.isAccountsEmailUniqueViolation(new DataIntegrityViolationException(
                "Unique index or primary key violation: \"UK_ACCOUNTS_EMAIL ON PUBLIC.ACCOUNTS(EMAIL)\"")));
        assertTrue(IdentityService.isAccountsEmailUniqueViolation(new DataIntegrityViolationException(
                "Unique index or primary key violation: \"public.uk_accounts_email_INDEX_8 ON public.accounts(email NULLS FIRST)\"")));
        assertFalse(IdentityService.isAccountsEmailUniqueViolation(otherUniqueViolation("uk_sessions_token")));
        assertFalse(IdentityService.isAccountsEmailUniqueViolation(new DataIntegrityViolationException(
                "Duplicate entry for key 'uk_idempotency'")));
    }

    @Test
    void loginUnknownEmailAndWrongPasswordShareTheSameUserCorrectableError() {
        AccountEntity account = account("acc-1", "seeker@example.com");
        when(accounts.findByEmail("nobody@example.com")).thenReturn(Optional.empty());
        when(accounts.findByEmail("seeker@example.com")).thenReturn(Optional.of(account));
        when(passwordEncoder.matches(any(), any())).thenReturn(false);

        AppException unknown = assertThrows(AppException.class, () -> service.login("nobody@example.com", "Passw0rd!"));
        AppException wrong = assertThrows(AppException.class, () -> service.login("seeker@example.com", "WrongPass1!"));

        assertEquals(unknown.reason(), wrong.reason());
        assertEquals(unknown.category(), wrong.category());
        assertEquals(unknown.getMessage(), wrong.getMessage());
        assertEquals("INVALID_CREDENTIALS", unknown.reason());
        assertEquals(ErrorCategory.USER_CORRECTABLE, unknown.category());
        assertEquals("邮箱、手机号或密码不正确", unknown.getMessage());
        verify(sessions, never()).save(any());
    }

    @Test
    void inactiveAndUnknownAccountsRemainIndistinguishableEvenWhenPasswordsMatch() {
        AccountEntity account = account("acc-1", "inactive@example.com");
        account.setStatus("DELETION_PENDING");
        when(accounts.findByEmail("inactive@example.com")).thenReturn(Optional.of(account));
        when(passwordEncoder.matches(any(), any())).thenReturn(true);
        AppException inactive = assertThrows(AppException.class, () -> service.login("inactive@example.com", "Passw0rd!"));
        AppException unknown = assertThrows(AppException.class, () -> service.login("missing@example.com", "Passw0rd!"));
        assertEquals("INVALID_CREDENTIALS", inactive.reason());
        assertEquals(inactive.reason(), unknown.reason());
        assertEquals(inactive.getMessage(), unknown.getMessage());
        verify(sessions, never()).save(any());
    }

    @Test
    void overlongPasswordIsRejectedBeforeLookupOrBcrypt() {
        for (String password : java.util.List.of("x".repeat(73), "\u4e2d".repeat(25))) {
            AppException ex = assertThrows(AppException.class, () -> service.login("seeker@example.com", password));
            assertEquals("INVALID_CREDENTIALS", ex.reason());
        }
        verify(passwordEncoder, never()).matches(any(), any());
        verify(accounts, never()).findByEmail(any());
        verify(sessions, never()).save(any());
    }

    @Test
    void accountThrottleStopsLookupAndBcryptAfterAdmittedAttempts() {
        when(accounts.findByEmail("nobody@example.com")).thenReturn(Optional.empty());
        for (int i = 0; i < 8; i++) {
            String clientIp = "192.0.2." + i;
            assertEquals("INVALID_CREDENTIALS", assertThrows(AppException.class,
                    () -> service.login("nobody@example.com", "WrongPass1!", clientIp)).reason());
        }
        assertEquals("LOGIN_RATE_LIMITED", assertThrows(AppException.class,
                () -> service.login("NOBODY@example.com", "WrongPass1!", "192.0.2.99")).reason());
        verify(accounts, org.mockito.Mockito.times(8)).findByEmail("nobody@example.com");
        verify(passwordEncoder, org.mockito.Mockito.times(8)).matches(any(), any());
    }

    @Test
    void disabledDevAdminAliasIsNotAcceptedAsAnEmailLogin() {
        AppException ex = assertThrows(AppException.class, () -> service.login("admin", "admin"));

        assertEquals("INVALID_CREDENTIALS", ex.reason());
        verify(accounts, never()).findByEmail(any());
        verify(sessions, never()).save(any());
    }

    private static DataIntegrityViolationException emailUniqueViolation() {
        return new DataIntegrityViolationException(
                "could not execute statement",
                new ConstraintViolationException(
                        "could not execute statement",
                        new SQLException("Duplicate entry 'seeker@example.com' for key 'uk_accounts_email'", "23000"),
                        "uk_accounts_email"));
    }

    private static DataIntegrityViolationException otherUniqueViolation(String constraintName) {
        return new DataIntegrityViolationException(
                "could not execute statement",
                new ConstraintViolationException(
                        "could not execute statement",
                        new SQLException("Duplicate entry 'x' for key '" + constraintName + "'", "23000"),
                        constraintName));
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

    private static final class MutableClock implements ClockPort {
        private final Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        @Override
        public Instant now() {
            return now;
        }
    }
}
