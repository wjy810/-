package com.jobproof.modules.site.web;

import com.jobproof.infrastructure.web.ApiResponse;
import com.jobproof.modules.site.application.PolicyDocumentService;
import com.jobproof.modules.site.application.PolicyDocumentService.PolicyDocument;
import com.jobproof.modules.site.application.SiteCapabilitiesService;
import com.jobproof.modules.site.application.SiteCapabilitiesService.Capabilities;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public, unauthenticated facts about the deployment. */
@RestController
@RequestMapping("/api/v1")
public class SiteController {
    private final SiteCapabilitiesService capabilities;
    private final PolicyDocumentService policies;

    public SiteController(SiteCapabilitiesService capabilities, PolicyDocumentService policies) {
        this.capabilities = capabilities;
        this.policies = policies;
    }

    @GetMapping("/capabilities")
    public ApiResponse<Capabilities> capabilities() {
        return ApiResponse.ok(capabilities.capabilities());
    }

    @GetMapping("/policies/{kind}")
    public ApiResponse<PolicyDocument> policy(@PathVariable String kind) {
        return ApiResponse.ok(policies.document(kind));
    }
}
