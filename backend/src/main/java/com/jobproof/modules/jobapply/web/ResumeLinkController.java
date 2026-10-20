package com.jobproof.modules.jobapply.web;

import org.springframework.web.bind.annotation.PatchMapping;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import com.jobproof.shared.auth.CurrentAccount;
import com.jobproof.modules.jobapply.application.ResumeLinkService.LinkPatch;
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
    private final OwnerDeviceCookie ownerDevices;

    public ResumeLinkController(ResumeLinkService links, OwnerDeviceCookie ownerDevices) {
        this.links = links;
        this.ownerDevices = ownerDevices;
    }

    @GetMapping
    public ApiResponse<List<LinkView>> list(HttpServletRequest request, HttpServletResponse response) {
        CurrentAccount current = SecurityConfig.currentAccount();
        ownerDevices.register(current, request, response);
        return ApiResponse.ok(links.list(current));
    }

    @PatchMapping("/{id}")
    public ApiResponse<LinkView> update(@PathVariable String id, @RequestBody LinkPatch patch) {
        return ApiResponse.ok(links.update(SecurityConfig.currentAccount(), id, patch));
    }

    /** 这是我自己打开的. */
    @PostMapping("/{id}/views/{viewId}/own")
    public ApiResponse<LinkView> markOwnView(@PathVariable String id, @PathVariable String viewId) {
        return ApiResponse.ok(links.markOwnView(SecurityConfig.currentAccount(), id, viewId));
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
