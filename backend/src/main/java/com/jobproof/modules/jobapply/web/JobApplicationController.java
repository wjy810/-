package com.jobproof.modules.jobapply.web;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import com.jobproof.shared.auth.CurrentAccount;
import com.jobproof.modules.jobapply.application.JobApplicationService.NextStepDone;
import com.jobproof.infrastructure.security.SecurityConfig;
import com.jobproof.infrastructure.web.ApiResponse;
import com.jobproof.modules.jobapply.application.JobApplicationService;
import com.jobproof.modules.jobapply.application.JobApplicationService.ApplicationView;
import com.jobproof.modules.jobapply.application.JobApplicationService.ApplicationWrite;
import com.jobproof.modules.jobapply.application.JobApplicationService.DetailView;
import com.jobproof.modules.jobapply.application.JobApplicationService.OverviewView;
import com.jobproof.modules.jobapply.application.JobApplicationService.StageWrite;
import com.jobproof.modules.jobapply.application.JobApplicationService.VersionOption;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 投递追踪 (docs/phase2/01). */
@RestController
@RequestMapping("/api/v1/job-applications")
public class JobApplicationController {

    private final JobApplicationService applications;
    private final OwnerDeviceCookie ownerDevices;

    public JobApplicationController(JobApplicationService applications, OwnerDeviceCookie ownerDevices) {
        this.applications = applications;
        this.ownerDevices = ownerDevices;
    }

    @GetMapping
    public ApiResponse<OverviewView> overview(HttpServletRequest request, HttpServletResponse response) {
        CurrentAccount current = SecurityConfig.currentAccount();
        ownerDevices.register(current, request, response);
        return ApiResponse.ok(applications.overview(current));
    }

    @GetMapping("/archived")
    public ApiResponse<List<ApplicationView>> archived() {
        return ApiResponse.ok(applications.archived(SecurityConfig.currentAccount()));
    }

    @PostMapping("/{id}/archive")
    public ApiResponse<ApplicationView> archive(@PathVariable String id) {
        return ApiResponse.ok(applications.archive(SecurityConfig.currentAccount(), id, true));
    }

    @PostMapping("/{id}/restore")
    public ApiResponse<ApplicationView> restore(@PathVariable String id) {
        return ApiResponse.ok(applications.archive(SecurityConfig.currentAccount(), id, false));
    }

    @PostMapping("/{id}/next-step/done")
    public ApiResponse<ApplicationView> completeNextStep(@PathVariable String id, @RequestBody(required = false) NextStepDone write) {
        return ApiResponse.ok(applications.completeNextStep(SecurityConfig.currentAccount(), id, write));
    }

    /** Frozen resume versions that can go out with an application. */
    @GetMapping("/resume-versions")
    public ApiResponse<List<VersionOption>> resumeVersions() {
        return ApiResponse.ok(applications.shareableVersions(SecurityConfig.currentAccount()));
    }

    @GetMapping("/{id}")
    public ApiResponse<DetailView> detail(@PathVariable String id) {
        return ApiResponse.ok(applications.detail(SecurityConfig.currentAccount(), id));
    }

    @PostMapping
    public ApiResponse<ApplicationView> create(@RequestBody ApplicationWrite write) {
        return ApiResponse.ok(applications.create(SecurityConfig.currentAccount(), write));
    }

    @PutMapping("/{id}")
    public ApiResponse<ApplicationView> update(@PathVariable String id, @RequestBody ApplicationWrite write) {
        return ApiResponse.ok(applications.update(SecurityConfig.currentAccount(), id, write));
    }

    @PostMapping("/{id}/stage")
    public ApiResponse<ApplicationView> moveStage(@PathVariable String id, @RequestBody StageWrite write) {
        return ApiResponse.ok(applications.moveStage(SecurityConfig.currentAccount(), id, write));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Map<String, Boolean>> delete(@PathVariable String id) {
        applications.delete(SecurityConfig.currentAccount(), id);
        return ApiResponse.ok(Map.of("deleted", true));
    }
}
