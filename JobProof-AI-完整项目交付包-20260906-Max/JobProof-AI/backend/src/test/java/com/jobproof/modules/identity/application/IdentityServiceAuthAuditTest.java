package com.jobproof.modules.identity.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import com.jobproof.modules.identity.infra.PasswordResetJpaRepository;
import com.jobproof.modules.identity.infra.SessionEntity;
import com.jobproof.modules.identity.infra.SessionJpaRepository;
import com.jobproof.shared.auth.CurrentAccount;
import com.jobproof.shared.error.AppException;
import com.jobproof.shared.time.ClockPort;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class IdentityServiceAuthAuditTest {

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

    private final ClockPort clock = () -> Instant.parse("2026-08-19T00:00:00Z");
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
    void registerWritesAccountRegisteredWithoutPassword() {
        when(accounts.existsByEmail("seeker@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Passw0rd!")).thenReturn("hash");
        when(accounts.saveAndFlush(any(AccountEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.register("Seeker@Example.com", "Passw0rd!");

        ArgumentCaptor<String> summary = ArgumentCaptor.forClass(String.class);
        verify(auditService).append(any(), eq("ACCOUNT_REGISTERED"), eq("ACCOUNT"), any(), summary.capture());
        assertEquals("邮箱注册成功", summary.getValue());
        assertFalse(summary.getValue().contains("Passw0rd!"));
    }

    @Test
    void loginWritesAccountLoginWithoutPassword() {
        AccountEntity account = account("acc-1", "seeker@example.com");
        when(accounts.findByEmail("seeker@example.com")).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("Passw0rd!", "old-hash")).thenReturn(true);
        when(sessions.save(any(SessionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.login("seeker@example.com", "Passw0rd!");

        ArgumentCaptor<String> summary = ArgumentCaptor.forClass(String.class);
        verify(auditService).append(eq("acc-1"), eq("ACCOUNT_LOGIN"), eq("SESSION"), any(), summary.capture());
        assertEquals("邮箱登录成功", summary.getValue());
        assertFalse(summary.getValue().contains("Passw0rd!"));
    }

    @Test
    void failedLoginWritesSanitizedAuditForKnownAndUnknownAccounts() {
        when(accounts.findByEmail("nobody@example.com")).thenReturn(Optional.empty());
        AccountEntity account = account("acc-1", "seeker@example.com");
        when(accounts.findByEmail("seeker@example.com")).thenReturn(Optional.of(account));
        when(passwordEncoder.matches(any(), any())).thenReturn(false);

        assertThrows(AppException.class, () -> service.login("nobody@example.com", "Passw0rd!"));
        assertThrows(AppException.class, () -> service.login("seeker@example.com", "WrongPass1!"));

        verify(auditService, org.mockito.Mockito.times(2)).append(
                org.mockito.ArgumentMatchers.isNull(), eq("ACCOUNT_LOGIN_FAILED"), eq("LOGIN_ATTEMPT"), any(), any());
        verify(sessions, never()).save(any());
    }

    @Test
    void logoutWritesAccountLogoutForCurrentSession() {
        SessionEntity session = new SessionEntity();
        session.setId("sess-1");
        session.setAccountId("acc-1");
        when(sessions.findById("sess-1")).thenReturn(Optional.of(session));

        service.logout(new CurrentAccount("acc-1", "seeker@example.com", "SEEKER", "sess-1"));

        verify(auditService).append("acc-1", "ACCOUNT_LOGOUT", "SESSION", "sess-1", "退出当前会话");
    }

    @Test
    void changePasswordWritesPasswordChangedAndRevokesSessions() {
        AccountEntity account = account("acc-1", "seeker@example.com");
        when(accounts.findById("acc-1")).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("Passw0rd!", "old-hash")).thenReturn(true);
        when(passwordEncoder.encode("Passw0rd2!")).thenReturn("new-hash");

        service.changePassword(
                new CurrentAccount("acc-1", "seeker@example.com", "SEEKER", "sess-1"),
                "Passw0rd!",
                "Passw0rd2!");

        verify(sessions).revokeAllByAccountId("acc-1", clock.now());
        ArgumentCaptor<String> summary = ArgumentCaptor.forClass(String.class);
        verify(auditService).append(eq("acc-1"), eq("PASSWORD_CHANGED"), eq("ACCOUNT"), eq("acc-1"), summary.capture());
        assertEquals("登录态改密，全部会话已失效", summary.getValue());
        assertFalse(summary.getValue().contains("Passw0rd!"));
        assertFalse(summary.getValue().contains("Passw0rd2!"));
    }

    @Test
    void wrongCurrentPasswordDoesNotWritePasswordChanged() {
        AccountEntity account = account("acc-1", "seeker@example.com");
        when(accounts.findById("acc-1")).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("WrongPass1!", "old-hash")).thenReturn(false);

        AppException ex = assertThrows(AppException.class, () -> service.changePassword(
                new CurrentAccount("acc-1", "seeker@example.com", "SEEKER", "sess-1"),
                "WrongPass1!",
                "Passw0rd2!"));

        assertEquals("INVALID_CREDENTIALS", ex.reason());
        verify(auditService, never()).append(any(), any(), any(), any(), any());
        verify(sessions, never()).revokeAllByAccountId(any(), any());
    }

    @Test
    void resetRequestWritesRequestedWithoutPlaintextCode() {
        AccountEntity account = account("acc-1", "seeker@example.com");
        when(accounts.findByEmail("seeker@example.com")).thenReturn(Optional.of(account));

        service.requestPasswordReset("seeker@example.com");

        verify(mailbox, never()).deliver(any());
        ArgumentCaptor<String> summary = ArgumentCaptor.forClass(String.class);
        verify(auditService).append(eq("acc-1"), eq("PASSWORD_RESET_REQUESTED"), eq("ACCOUNT"), eq("acc-1"), summary.capture());
        assertEquals("已发送邮箱验证码", summary.getValue());
        assertFalse(summary.getValue().matches(".*\\b\\d{6}\\b.*"));
    }

    @Test
    void unknownEmailResetRequestDoesNotWriteAudit() {
        when(accounts.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        service.requestPasswordReset("nobody@example.com");

        verify(auditService, never()).append(any(), any(), any(), any(), any());
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
}
