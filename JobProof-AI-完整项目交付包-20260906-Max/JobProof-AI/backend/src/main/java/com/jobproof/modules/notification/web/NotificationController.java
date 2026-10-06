package com.jobproof.modules.notification.web;

import com.jobproof.infrastructure.security.SecurityConfig;
import com.jobproof.infrastructure.web.ApiResponse;
import com.jobproof.modules.notification.application.NotificationService;
import com.jobproof.modules.notification.application.NotificationService.NotificationView;
import com.jobproof.shared.page.PageQuery;
import com.jobproof.shared.page.PageResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public ApiResponse<PageResult<NotificationView>> list(
            @RequestParam(required = false) String status,
            @ModelAttribute PageQuery query) {
        return ApiResponse.ok(notificationService.list(SecurityConfig.currentAccount().accountId(), status, query));
    }

    @GetMapping("/unread-count")
    public ApiResponse<UnreadCountView> unreadCount() {
        return ApiResponse.ok(new UnreadCountView(
                notificationService.unreadCount(SecurityConfig.currentAccount().accountId())));
    }

    @PostMapping("/{id}/read")
    public ApiResponse<Void> read(@PathVariable String id) {
        notificationService.markRead(SecurityConfig.currentAccount().accountId(), id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/read-batch")
    public ApiResponse<Void> readBatch(@Valid @RequestBody ReadBatchRequest request) {
        notificationService.markReadBatch(SecurityConfig.currentAccount().accountId(), request.ids());
        return ApiResponse.ok(null);
    }

    public record ReadBatchRequest(@NotEmpty List<String> ids) {
    }

    public record UnreadCountView(long count) {
    }
}
