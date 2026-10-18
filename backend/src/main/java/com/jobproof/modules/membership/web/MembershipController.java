package com.jobproof.modules.membership.web;

import com.jobproof.infrastructure.security.SecurityConfig;
import com.jobproof.infrastructure.web.ApiResponse;
import com.jobproof.modules.membership.application.MembershipService;
import com.jobproof.modules.membership.application.MembershipService.MembershipView;
import com.jobproof.modules.membership.application.MembershipService.PlanCatalog;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 会员与兑换码 (docs/phase2/02): the caller's plan, redeeming a code, and the public plan catalog. */
@RestController
@RequestMapping("/api/v1/membership")
public class MembershipController {

    private final MembershipService memberships;

    public MembershipController(MembershipService memberships) {
        this.memberships = memberships;
    }

    @GetMapping
    public ApiResponse<MembershipView> current() {
        return ApiResponse.ok(memberships.current(SecurityConfig.currentAccount()));
    }

    /** Public: what each plan includes and what PRO costs. */
    @GetMapping("/plans")
    public ApiResponse<PlanCatalog> plans() {
        return ApiResponse.ok(memberships.catalog());
    }

    @PostMapping("/redeem")
    public ApiResponse<MembershipView> redeem(@RequestBody RedeemRequest request) {
        return ApiResponse.ok(memberships.redeem(SecurityConfig.currentAccount(), request == null ? null : request.code()));
    }

    public record RedeemRequest(String code) {
    }
}
