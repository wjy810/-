package com.jobproof.modules.identity.web;

import com.jobproof.infrastructure.security.SecurityConfig;
import com.jobproof.infrastructure.security.SessionAuthFilter;
import com.jobproof.infrastructure.config.JobProofProperties;
import com.jobproof.infrastructure.web.ApiResponse;
import com.jobproof.modules.identity.application.IdentityService;
import com.jobproof.modules.identity.application.IdentityService.AccountView;
import com.jobproof.modules.identity.application.IdentityService.LoginResult;
import com.jobproof.modules.identity.application.IdentityService.SessionView;
import com.jobproof.modules.identity.domain.VerificationChannel;
import com.jobproof.shared.auth.CurrentAccount;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class AuthController {

    private final IdentityService identityService;
    private final SessionAuthFilter sessionAuthFilter;
    private final JobProofProperties properties;

    public AuthController(
            IdentityService identityService,
            SessionAuthFilter sessionAuthFilter,
            JobProofProperties properties) {
        this.identityService = identityService;
        this.sessionAuthFilter = sessionAuthFilter;
        this.properties = properties;
    }

    @PostMapping("/auth/register")
    public ApiResponse<AccountView> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse response) {
        if (request.channel() == null && properties.getVerification().isLegacyTestCompatibilityEnabled()) {
            return ApiResponse.ok(identityService.register(request.email(), request.password()));
        }
        requireNewRegistration(request);
        LoginResult result = identityService.register(
                request.channel(),
                request.destination(),
                request.verificationToken(),
                request.password(),
                request.acceptedTerms(),
                request.acceptedPrivacy());
        writeSessionCookie(servletRequest, response, result, true);
        return ApiResponse.ok(result.account());
    }

    @PostMapping("/auth/login")
    public ApiResponse<AccountView> login(@Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest, HttpServletResponse response) {
        String identifier = request.identifier() == null || request.identifier().isBlank()
                ? request.email()
                : request.identifier();
        if (identifier == null || identifier.isBlank()) {
            throw com.jobproof.shared.error.AppException.user("IDENTIFIER_REQUIRED", "请输入邮箱或手机号");
        }
        LoginResult result = identityService.login(identifier, request.password(), servletRequest.getRemoteAddr());
        writeSessionCookie(servletRequest, response, result, !Boolean.FALSE.equals(request.rememberMe()));
        return ApiResponse.ok(result.account());
    }

    @PostMapping("/auth/logout")
    public ApiResponse<Void> logout(HttpServletRequest servletRequest, HttpServletResponse response) {
        identityService.logout(SecurityConfig.currentAccount());
        sessionAuthFilter.clearSessionCookie(servletRequest, response);
        return ApiResponse.ok(null);
    }

    @PostMapping("/auth/password/change")
    public ApiResponse<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request, HttpServletRequest servletRequest, HttpServletResponse response) {
        identityService.changePassword(SecurityConfig.currentAccount(), request.currentPassword(), request.newPassword());
        sessionAuthFilter.clearSessionCookie(servletRequest, response);
        return ApiResponse.ok(null);
    }

    @PostMapping("/auth/password/reset/confirm")
    public ApiResponse<Void> confirmReset(@Valid @RequestBody ResetConfirmRequest request) {
        if (request.channel() == null && properties.getVerification().isLegacyTestCompatibilityEnabled()) {
            identityService.confirmPasswordReset(request.email(), request.code(), request.newPassword());
            return ApiResponse.ok(null);
        }
        if (request.channel() == null || blank(request.destination()) || blank(request.verificationToken())) {
            throw com.jobproof.shared.error.AppException.user("RESET_REQUEST_INVALID", "找回密码验证信息不完整");
        }
        identityService.confirmPasswordReset(
                request.channel(), request.destination(), request.verificationToken(), request.newPassword());
        return ApiResponse.ok(null);
    }

    @PostMapping("/auth/password/reset/request")
    public ApiResponse<Void> requestLegacyReset(@Valid @RequestBody LegacyResetRequest request) {
        if (!properties.getVerification().isLegacyTestCompatibilityEnabled()) {
            throw com.jobproof.shared.error.AppException.user(
                    "LEGACY_AUTH_FLOW_DISABLED", "请通过统一身份验证流程找回密码");
        }
        identityService.requestPasswordReset(request.email());
        return ApiResponse.ok(null);
    }

    @GetMapping("/me")
    public ApiResponse<AccountView> me() {
        CurrentAccount current = SecurityConfig.currentAccount();
        return ApiResponse.ok(identityService.current(current));
    }

    @GetMapping("/auth/sessions")
    public ApiResponse<List<SessionView>> sessions() {
        return ApiResponse.ok(identityService.listSessions(SecurityConfig.currentAccount()));
    }

    @DeleteMapping("/auth/sessions/{id}")
    public ApiResponse<Void> revokeSession(@PathVariable String id) {
        identityService.revokeSession(SecurityConfig.currentAccount(), id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/auth/sessions/revoke-others")
    public ApiResponse<Map<String, Integer>> revokeOtherSessions() {
        int revoked = identityService.revokeOtherSessions(SecurityConfig.currentAccount());
        return ApiResponse.ok(Map.of("revoked", revoked));
    }

    private void writeSessionCookie(HttpServletRequest request, HttpServletResponse response, LoginResult result, boolean persistent) {
        long maxAge = persistent
                ? Math.max(0, result.expiresAt().getEpochSecond() - Instant.now().getEpochSecond())
                : -1;
        sessionAuthFilter.writeSessionCookie(request, response, result.rawSessionToken(), maxAge);
    }

    private static void requireNewRegistration(RegisterRequest request) {
        if (request.channel() == null || blank(request.destination()) || blank(request.verificationToken())) {
            throw com.jobproof.shared.error.AppException.user("REGISTER_REQUEST_INVALID", "注册验证信息不完整");
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    public record RegisterRequest(
            VerificationChannel channel,
            String destination,
            String verificationToken,
            String email,
            @NotBlank String password,
            boolean acceptedTerms,
            boolean acceptedPrivacy) {
    }

    public record LoginRequest(String identifier, String email, @NotBlank String password, Boolean rememberMe) {
    }

    public record ChangePasswordRequest(@NotBlank String currentPassword, @NotBlank String newPassword) {
    }

    public record ResetConfirmRequest(
            VerificationChannel channel,
            String destination,
            String verificationToken,
            String email,
            String code,
            @NotBlank String newPassword) {
    }

    public record LegacyResetRequest(@NotBlank String email) {
    }
}
