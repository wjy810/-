package com.jobproof.modules.audit.web;

import com.jobproof.infrastructure.security.SecurityConfig;
import com.jobproof.infrastructure.web.ApiResponse;
import com.jobproof.modules.audit.application.AuditQueryService;
import com.jobproof.modules.audit.application.AuditQueryService.EventView;
import com.jobproof.shared.page.PageResult;
import java.time.Instant;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/audit-events")
public class AdminAuditController {
    private final AuditQueryService audit;
    public AdminAuditController(AuditQueryService audit) { this.audit = audit; }
    @GetMapping
    public ApiResponse<PageResult<EventView>> list(@RequestParam(required=false) String actor,
            @RequestParam(required=false) String action, @RequestParam(required=false) String objectType,
            @RequestParam(required=false) String objectId, @RequestParam(required=false) Instant from,
            @RequestParam(required=false) Instant to, @RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="20") int size) {
        return ApiResponse.ok(audit.list(SecurityConfig.currentAccount(), actor, action, objectType, objectId, from, to, page, size));
    }
}
