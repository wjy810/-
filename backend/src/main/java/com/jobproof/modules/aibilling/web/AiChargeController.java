package com.jobproof.modules.aibilling.web;

import com.jobproof.infrastructure.security.SecurityConfig;
import com.jobproof.infrastructure.web.ApiResponse;
import com.jobproof.modules.aibilling.application.AiChargeService;
import com.jobproof.modules.airesume.application.AiQuotaService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/ai/charges")
public class AiChargeController {
    private final AiChargeService charges;
    private final AiQuotaService quotas;
    public AiChargeController(AiChargeService charges, AiQuotaService quotas) { this.charges = charges; this.quotas = quotas; }
    @PostMapping("/quote")
    public ApiResponse<AiChargeService.Quote> quote(@Valid @RequestBody QuoteRequest request) {
        String account = SecurityConfig.currentAccount().accountId();
        return ApiResponse.ok(charges.quote(account, request.path(), request.body(), quotas.current(account).remainingUnits()));
    }
    public record QuoteRequest(@NotBlank @Size(max = 512) String path, @Size(max = 2000000) String body) {}

    @GetMapping("/usage/{usageId}/attempts")
    public ApiResponse<?> attempts(@PathVariable String usageId) {
        return ApiResponse.ok(charges.attempts(SecurityConfig.currentAccount(), usageId));
    }
}
