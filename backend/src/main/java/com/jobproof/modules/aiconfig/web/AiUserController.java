package com.jobproof.modules.aiconfig.web;

import com.jobproof.infrastructure.security.SecurityConfig;
import com.jobproof.infrastructure.web.ApiResponse;
import com.jobproof.modules.aiconfig.application.AiUserChannelService;
import com.jobproof.modules.aiconfig.application.AiUserChannelService.ChannelView;
import com.jobproof.modules.aiconfig.application.AiUserChannelService.CreateCommand;
import com.jobproof.modules.aiconfig.application.AiUserChannelService.TestView;
import com.jobproof.modules.aiconfig.application.AiUserChannelService.UpdateCommand;
import com.jobproof.modules.aibilling.application.AiUserBillingQueryService;
import com.jobproof.modules.aibilling.application.AiUserBillingQueryService.BillingView;
import com.jobproof.modules.aibilling.application.AiUserBillingQueryService.LedgerView;
import com.jobproof.modules.aibilling.application.AiUserBillingQueryService.SubscriptionView;
import com.jobproof.modules.aibilling.application.AiUserBillingQueryService.UsageView;
import com.jobproof.modules.aibilling.application.AiUserBillingQueryService.WalletView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/ai")
public class AiUserController {
    private final AiUserChannelService channels;
    private final AiUserBillingQueryService billing;

    public AiUserController(AiUserChannelService channels, AiUserBillingQueryService billing) {
        this.channels = channels;
        this.billing = billing;
    }

    @GetMapping("/channels")
    public ApiResponse<PageResponse<ChannelView>> channels(
            @RequestParam(defaultValue = "SYSTEM") String scope,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ApiResponse.ok(PageResponse.from(channels.list(SecurityConfig.currentAccount(), scope, status, pageable)));
    }

    @PostMapping("/channels/personal")
    public ApiResponse<ChannelView> create(@Valid @RequestBody CreatePersonalChannelRequest request) {
        return ApiResponse.ok(channels.create(SecurityConfig.currentAccount(), new CreateCommand(
                request.name(), request.providerCode(), request.baseUrl(), request.apiKey(), request.protocol(),
                request.idempotencyKey())));
    }

    @PutMapping("/channels/personal/{channelId}")
    public ApiResponse<ChannelView> update(@PathVariable String channelId,
            @Valid @RequestBody UpdatePersonalChannelRequest request) {
        return ApiResponse.ok(channels.update(SecurityConfig.currentAccount(), channelId, new UpdateCommand(
                request.name(), request.baseUrl(), request.apiKey(), request.status(), request.versionNo(),
                request.idempotencyKey())));
    }

    @PostMapping("/channels/personal/{channelId}/test")
    public ApiResponse<TestView> test(@PathVariable String channelId, @Valid @RequestBody TestChannelRequest request) {
        return ApiResponse.ok(channels.test(SecurityConfig.currentAccount(), channelId, request.idempotencyKey()));
    }

    @PostMapping("/channels/personal/{channelId}/archive")
    public ApiResponse<ChannelView> archive(@PathVariable String channelId,
            @Valid @RequestBody VersionedCommandRequest request) {
        return ApiResponse.ok(channels.archive(SecurityConfig.currentAccount(), channelId,
                request.versionNo(), request.idempotencyKey()));
    }

    @GetMapping("/wallet")
    public ApiResponse<WalletView> wallet() {
        return ApiResponse.ok(billing.wallet(SecurityConfig.currentAccount()));
    }

    @GetMapping("/wallet/ledger")
    public ApiResponse<PageResponse<LedgerView>> ledger(@RequestParam(required = false) String type,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.ok(PageResponse.from(billing.ledger(SecurityConfig.currentAccount(), type,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")))));
    }

    @GetMapping("/usage")
    public ApiResponse<PageResponse<UsageView>> usage(@RequestParam(required = false) String status,
            @RequestParam(required = false) String from, @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.ok(PageResponse.from(billing.usage(SecurityConfig.currentAccount(),
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "startedAt")))));
    }

    @GetMapping("/billing")
    public ApiResponse<PageResponse<BillingView>> billing(@RequestParam(required = false) String status,
            @RequestParam(required = false) String from, @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.ok(PageResponse.from(billing.billing(SecurityConfig.currentAccount(),
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")))));
    }

    @GetMapping("/subscriptions/personal-channel")
    public ApiResponse<SubscriptionView> subscription() {
        return ApiResponse.ok(billing.subscription(SecurityConfig.currentAccount()));
    }

    @PostMapping("/subscriptions/personal-channel")
    public ApiResponse<SubscriptionView> subscribe(@Valid @RequestBody SubscribeRequest request) {
        return ApiResponse.ok(billing.unsupportedSubscriptionWrite(SecurityConfig.currentAccount()));
    }

    @PostMapping("/subscriptions/personal-channel/cancel-renewal")
    public ApiResponse<SubscriptionView> cancelRenewal(@Valid @RequestBody VersionedCommandRequest request) {
        return ApiResponse.ok(billing.unsupportedSubscriptionWrite(SecurityConfig.currentAccount()));
    }

    public record CreatePersonalChannelRequest(@NotBlank String name, @NotBlank String providerCode,
            @NotBlank String baseUrl, @NotBlank String apiKey, @NotBlank String protocol,
            @NotBlank @Size(max = 128) String idempotencyKey) {}
    public record UpdatePersonalChannelRequest(@NotBlank String name, @NotBlank String baseUrl, String apiKey,
            @NotBlank String status, @Min(0) int versionNo,
            @NotBlank @Size(max = 128) String idempotencyKey) {}
    public record VersionedCommandRequest(@Min(0) int versionNo,
            @NotBlank @Size(max = 128) String idempotencyKey) {}
    public record TestChannelRequest(@NotBlank @Size(max = 128) String idempotencyKey) {}
    public record SubscribeRequest(boolean autoRenew, @Min(0) int versionNo,
            @NotBlank @Size(max = 128) String idempotencyKey) {}
    public record PageResponse<T>(List<T> items, int page, int size, long totalElements, int totalPages) {
        static <T> PageResponse<T> from(Page<T> page) {
            return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                    page.getTotalElements(), page.getTotalPages());
        }
    }
}
