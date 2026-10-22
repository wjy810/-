package com.jobproof.modules.aibilling.web;

import com.jobproof.infrastructure.security.SecurityConfig;
import com.jobproof.infrastructure.web.ApiResponse;
import com.jobproof.modules.membership.application.MembershipService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/commerce")
public class WalletRedemptionController {
    private final MembershipService memberships;
    public WalletRedemptionController(MembershipService memberships) { this.memberships = memberships; }
    @PostMapping("/redeem")
    public ApiResponse<MembershipService.RedemptionReceipt> redeem(@Valid @RequestBody RedeemRequest request) {
        return ApiResponse.ok(memberships.redeemWithReceipt(SecurityConfig.currentAccount(), request.code()));
    }
    public record RedeemRequest(@NotBlank @Size(max = 128) String code) {}
}
