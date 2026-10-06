package com.jobproof.modules.identity.web;

import com.jobproof.infrastructure.web.ApiResponse;
import com.jobproof.modules.identity.application.ContactVerificationService;
import com.jobproof.modules.identity.application.ContactVerificationService.Capabilities;
import com.jobproof.modules.identity.application.ContactVerificationService.ConfirmationResult;
import com.jobproof.modules.identity.application.ContactVerificationService.RequestResult;
import com.jobproof.modules.identity.domain.VerificationChannel;
import com.jobproof.modules.identity.domain.VerificationPurpose;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth/verifications")
public class ContactVerificationController {

    private final ContactVerificationService verificationService;

    public ContactVerificationController(ContactVerificationService verificationService) {
        this.verificationService = verificationService;
    }

    @GetMapping("/capabilities")
    public ApiResponse<Capabilities> capabilities() {
        return ApiResponse.ok(verificationService.capabilities());
    }

    @PostMapping("/request")
    public ApiResponse<RequestResult> request(
            @Valid @RequestBody VerificationRequest request,
            HttpServletRequest servletRequest) {
        return ApiResponse.ok(verificationService.request(
                request.channel(), request.destination(), request.purpose(), servletRequest.getRemoteAddr()));
    }

    @PostMapping("/confirm")
    public ApiResponse<ConfirmationResult> confirm(@Valid @RequestBody VerificationConfirmRequest request) {
        return ApiResponse.ok(verificationService.confirm(
                request.challengeId(), request.destination(), request.code()));
    }

    public record VerificationRequest(
            @NotNull VerificationChannel channel,
            @NotBlank String destination,
            @NotNull VerificationPurpose purpose) {
    }

    public record VerificationConfirmRequest(
            @NotBlank String challengeId,
            @NotBlank String destination,
            @NotBlank String code) {
    }
}
