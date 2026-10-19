package com.jobproof.modules.notification.web;

import com.jobproof.infrastructure.security.SecurityConfig;
import com.jobproof.infrastructure.web.ApiResponse;
import com.jobproof.modules.notification.application.NotificationPreferences;
import com.jobproof.modules.notification.application.NotificationPreferences.ItemView;
import com.jobproof.modules.notification.application.NotificationPreferences.PreferencesView;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 通知偏好 (docs/phase2/03): what shows in the notification center and what is emailed. */
@RestController
@RequestMapping("/api/v1/notification-preferences")
public class NotificationPreferenceController {

    private final NotificationPreferences preferences;

    public NotificationPreferenceController(NotificationPreferences preferences) {
        this.preferences = preferences;
    }

    @GetMapping
    public ApiResponse<PreferencesView> view() {
        return ApiResponse.ok(preferences.view(SecurityConfig.currentAccount()));
    }

    @PutMapping
    public ApiResponse<PreferencesView> update(@RequestBody UpdateRequest request) {
        return ApiResponse.ok(preferences.update(SecurityConfig.currentAccount(), request == null ? null : request.items()));
    }

    public record UpdateRequest(List<ItemView> items) {
    }
}
