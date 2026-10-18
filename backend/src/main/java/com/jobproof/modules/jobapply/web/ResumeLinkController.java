package com.jobproof.modules.jobapply.web;

import com.jobproof.infrastructure.security.SecurityConfig;
import com.jobproof.infrastructure.web.ApiResponse;
import com.jobproof.modules.jobapply.application.ResumeLinkService;
import com.jobproof.modules.jobapply.application.ResumeLinkService.LinkCommand;
import com.jobproof.modules.jobapply.application.ResumeLinkService.LinkView;
import com.jobproof.modules.jobapply.application.ResumeLinkService.ViewRecord;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The owner's side of resume links: create, revoke, read receipts. */
@RestController
@RequestMapping("/api/v1/resume-links")
public class ResumeLinkController {

    private final ResumeLinkService links;

    public ResumeLinkController(ResumeLinkService links) {
        this.links = links;
    }

    @GetMapping
    public ApiResponse<List<LinkView>> list() {
        return ApiResponse.ok(links.list(SecurityConfig.currentAccount()));
    }

    @PostMapping
    public ApiResponse<LinkView> create(@RequestBody LinkCommand command) {
        return ApiResponse.ok(links.create(SecurityConfig.currentAccount(), command));
    }

    @PostMapping("/{id}/revoke")
    public ApiResponse<LinkView> revoke(@PathVariable String id) {
        return ApiResponse.ok(links.revoke(SecurityConfig.currentAccount(), id));
    }

    @GetMapping("/{id}/views")
    public ApiResponse<List<ViewRecord>> views(@PathVariable String id) {
        return ApiResponse.ok(links.views(SecurityConfig.currentAccount(), id));
    }
}
