package com.jobproof.infrastructure.web;

import com.jobproof.infrastructure.config.JobProofProperties;
import com.jobproof.infrastructure.mail.DevMailbox;
import com.jobproof.shared.error.AppException;
import java.time.Instant;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping
public class HealthAndDevController {

    private final DevMailbox mailbox;
    private final JobProofProperties properties;

    public HealthAndDevController(DevMailbox mailbox, JobProofProperties properties) {
        this.mailbox = mailbox;
        this.properties = properties;
    }

    @GetMapping("/api/v1/health")
    public ApiResponse<Map<String, Object>> health() {
        return ApiResponse.ok(Map.of(
                "status", "UP",
                "slice", "S5",
                "time", Instant.now().toString()));
    }

    @GetMapping("/internal/dev/mailbox/{email}")
    public ApiResponse<Map<String, String>> mailbox(@PathVariable String email) {
        if (!properties.getDev().isMailboxEnabled()) {
            throw AppException.forbidden("DEV_MAILBOX_DISABLED", "开发邮箱仅在 dev/local/test 可用");
        }
        return mailbox.lastTo(email)
                .map(mail -> ApiResponse.ok(Map.of("to", mail.to(), "purpose", mail.purpose(), "code", mail.code())))
                .orElseThrow(() -> AppException.user("MAIL_NOT_FOUND", "尚无该邮箱的开发信件"));
    }
}
