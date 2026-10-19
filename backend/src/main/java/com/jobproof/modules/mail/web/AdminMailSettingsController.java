package com.jobproof.modules.mail.web;

import com.jobproof.infrastructure.security.SecurityConfig;
import com.jobproof.infrastructure.web.ApiResponse;
import com.jobproof.modules.mail.application.MailSettingsService;
import com.jobproof.modules.mail.application.MailSettingsService.SettingsCommand;
import com.jobproof.modules.mail.application.MailSettingsService.SettingsView;
import com.jobproof.modules.mail.application.MailSettingsService.TestResult;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 后台邮件设置 (docs/phase2/05): administrators only. */
@RestController
@RequestMapping("/api/v1/admin/mail-settings")
public class AdminMailSettingsController {

    private final MailSettingsService settings;

    public AdminMailSettingsController(MailSettingsService settings) {
        this.settings = settings;
    }

    @GetMapping
    public ApiResponse<SettingsView> view() {
        return ApiResponse.ok(settings.view(SecurityConfig.currentAccount()));
    }

    @PutMapping
    public ApiResponse<SettingsView> save(@RequestBody SettingsCommand command) {
        return ApiResponse.ok(settings.save(SecurityConfig.currentAccount(), command));
    }

    @DeleteMapping
    public ApiResponse<SettingsView> clear() {
        return ApiResponse.ok(settings.clear(SecurityConfig.currentAccount()));
    }

    @PostMapping("/test")
    public ApiResponse<TestResult> test(@RequestBody TestRequest request) {
        return ApiResponse.ok(settings.test(SecurityConfig.currentAccount(), request == null ? null : request.to()));
    }

    public record TestRequest(String to) {
    }
}
