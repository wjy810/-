package com.jobproof.modules.aiconfig.web;

import com.jobproof.infrastructure.security.SecurityConfig;
import com.jobproof.infrastructure.web.ApiResponse;
import com.jobproof.modules.aiconfig.application.AiAdminChannelService;
import com.jobproof.modules.aiconfig.application.AiAdminChannelService.CapabilityView;
import com.jobproof.modules.aiconfig.application.AiAdminChannelService.CreateSystemChannelCommand;
import com.jobproof.modules.aiconfig.application.AiAdminChannelService.SystemChannelView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/ai/channels")
public class AiAdminChannelController {
    private final AiAdminChannelService service;

    public AiAdminChannelController(AiAdminChannelService service) { this.service = service; }

    @GetMapping
    public ApiResponse<List<SystemChannelView>> list() {
        return ApiResponse.ok(service.list(SecurityConfig.currentAccount()));
    }

    @PostMapping
    public ApiResponse<SystemChannelView> create(@Valid @RequestBody CreateRequest request) {
        return ApiResponse.ok(service.create(SecurityConfig.currentAccount(), new CreateSystemChannelCommand(
                request.name(), request.providerCode(), request.baseUrl(), request.apiKey(), request.protocol())));
    }

    @PostMapping("/{channelId}/capability-test")
    public ApiResponse<CapabilityView> capabilityTest(@PathVariable String channelId,
            @Valid @RequestBody CapabilityRequest request) {
        return ApiResponse.ok(service.capabilityTest(SecurityConfig.currentAccount(), channelId,
                request.providerModel(), request.platformModelCode()));
    }

    @PostMapping("/{channelId}/activate")
    public ApiResponse<SystemChannelView> activate(@PathVariable String channelId,
            @Valid @RequestBody VersionRequest request) {
        return ApiResponse.ok(service.activate(SecurityConfig.currentAccount(), channelId, request.expectedVersion()));
    }

    @PostMapping("/{channelId}/disable")
    public ApiResponse<SystemChannelView> disable(@PathVariable String channelId,
            @Valid @RequestBody VersionRequest request) {
        return ApiResponse.ok(service.disable(SecurityConfig.currentAccount(), channelId, request.expectedVersion()));
    }

    public record CreateRequest(@NotBlank @Size(max = 128) String name,
            @NotBlank @Size(max = 64) String providerCode, @NotBlank @Size(max = 1024) String baseUrl,
            @NotBlank @Size(max = 4096) String apiKey, @NotBlank String protocol) {}
    public record CapabilityRequest(@Size(max = 128) String providerModel,
            @NotBlank @Size(max = 128) String platformModelCode) {}
    public record VersionRequest(@Min(0) int expectedVersion) {}
}
