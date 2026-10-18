package com.jobproof.modules.membership.web;

import com.jobproof.infrastructure.security.SecurityConfig;
import com.jobproof.infrastructure.web.ApiResponse;
import com.jobproof.modules.membership.application.MembershipService;
import com.jobproof.modules.membership.application.MembershipService.BatchCommand;
import com.jobproof.modules.membership.application.MembershipService.BatchCreated;
import com.jobproof.modules.membership.application.MembershipService.BatchView;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Redeem code batches for schools, training providers and promotions (administrators only). */
@RestController
@RequestMapping("/api/v1/admin/redeem-codes")
public class AdminRedeemCodeController {

    private final MembershipService memberships;

    public AdminRedeemCodeController(MembershipService memberships) {
        this.memberships = memberships;
    }

    @GetMapping
    public ApiResponse<List<BatchView>> batches() {
        return ApiResponse.ok(memberships.batches(SecurityConfig.currentAccount()));
    }

    /** The plain codes are in this response only; download them now. */
    @PostMapping
    public ApiResponse<BatchCreated> create(@RequestBody BatchCommand command) {
        return ApiResponse.ok(memberships.createBatch(SecurityConfig.currentAccount(), command));
    }

    @PostMapping("/{id}/revoke")
    public ApiResponse<BatchView> revoke(@PathVariable String id) {
        return ApiResponse.ok(memberships.revokeBatch(SecurityConfig.currentAccount(), id));
    }
}
