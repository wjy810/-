package com.jobproof.modules.operations.web;

import com.jobproof.infrastructure.security.SecurityConfig;
import com.jobproof.infrastructure.web.ApiResponse;
import com.jobproof.modules.operations.application.OperationsOverviewService;
import com.jobproof.modules.operations.application.OperationsOverviewService.Overview;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 运营概览 (docs/phase2/04): administrators only. */
@RestController
@RequestMapping("/api/v1/admin/overview")
public class AdminOverviewController {

    private final OperationsOverviewService overview;

    public AdminOverviewController(OperationsOverviewService overview) {
        this.overview = overview;
    }

    @GetMapping
    public ApiResponse<Overview> overview() {
        return ApiResponse.ok(overview.overview(SecurityConfig.currentAccount()));
    }
}
