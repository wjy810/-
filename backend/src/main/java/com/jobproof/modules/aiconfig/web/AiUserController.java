package com.jobproof.modules.aiconfig.web;

import com.jobproof.infrastructure.security.SecurityConfig;
import com.jobproof.infrastructure.web.ApiResponse;
import com.jobproof.modules.aibilling.application.AiUserBillingQueryService;
import com.jobproof.modules.aibilling.application.AiUserBillingQueryService.BillingView;
import com.jobproof.modules.aibilling.application.AiUserBillingQueryService.LedgerView;
import com.jobproof.modules.aibilling.application.AiUserBillingQueryService.SubscriptionView;
import com.jobproof.modules.aibilling.application.AiUserBillingQueryService.UsageView;
import com.jobproof.modules.aibilling.application.AiUserBillingQueryService.WalletView;
import com.jobproof.modules.aiconfig.application.AiAdminChannelService.CapabilityView;
import com.jobproof.modules.aiconfig.application.AiAdminChannelService.SystemChannelView;
import com.jobproof.modules.aiconfig.application.AiUserChannelService;
import com.jobproof.modules.aiconfig.application.AiUserChannelService.CreateCommand;
import com.jobproof.modules.aiconfig.application.AiUserChannelService.PreferenceCommand;
import com.jobproof.modules.aiconfig.application.AiUserChannelService.PreferenceView;
import com.jobproof.modules.aiconfig.application.AiUserChannelService.UpdateCommand;
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
  private final com.jobproof.modules.aibilling.application.CommercePurchaseService purchases;

  public AiUserController(
      AiUserChannelService channels,
      AiUserBillingQueryService billing,
      com.jobproof.modules.aibilling.application.CommercePurchaseService purchases) {
    this.channels = channels;
    this.billing = billing;
    this.purchases = purchases;
  }

  @GetMapping("/channels")
  public ApiResponse<PageResponse<SystemChannelView>> channels(
      @RequestParam(defaultValue = "SYSTEM") String scope,
      @RequestParam(required = false) String status,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
    return ApiResponse.ok(
        PageResponse.from(channels.list(SecurityConfig.currentAccount(), scope, status, pageable)));
  }

  @PostMapping("/channels/personal")
  public ApiResponse<SystemChannelView> create(
      @Valid @RequestBody CreatePersonalChannelRequest request) {
    return ApiResponse.ok(
        channels.create(
            SecurityConfig.currentAccount(),
            new CreateCommand(
                request.name(),
                request.providerCode(),
                request.baseUrl(),
                request.apiKey(),
                request.protocol(),
                request.idempotencyKey())));
  }

  @PutMapping("/channels/personal/{channelId}")
  public ApiResponse<SystemChannelView> update(
      @PathVariable String channelId, @Valid @RequestBody UpdatePersonalChannelRequest request) {
    return ApiResponse.ok(
        channels.update(
            SecurityConfig.currentAccount(),
            channelId,
            new UpdateCommand(
                request.name(),
                request.providerCode(),
                request.baseUrl(),
                request.apiKey(),
                request.protocol(),
                request.selectedModel(),
                request.status(),
                request.versionNo(),
                request.idempotencyKey())));
  }

  @PostMapping("/channels/personal/{channelId}/test")
  public ApiResponse<CapabilityView> test(
      @PathVariable String channelId, @Valid @RequestBody TestChannelRequest request) {
    return ApiResponse.ok(
        channels.test(
            SecurityConfig.currentAccount(),
            channelId,
            request.providerModel(),
            request.idempotencyKey()));
  }

  @PostMapping("/channels/personal/{channelId}/activate")
  public ApiResponse<SystemChannelView> activate(
      @PathVariable String channelId, @Valid @RequestBody ActivationRequest request) {
    return ApiResponse.ok(
        channels.activate(
            SecurityConfig.currentAccount(),
            channelId,
            request.versionNo(),
            request.providerModel(),
            request.idempotencyKey()));
  }

  @PostMapping("/channels/personal/{channelId}/disable")
  public ApiResponse<SystemChannelView> disable(
      @PathVariable String channelId, @Valid @RequestBody VersionedCommandRequest request) {
    return ApiResponse.ok(
        channels.disable(
            SecurityConfig.currentAccount(),
            channelId,
            request.versionNo(),
            request.idempotencyKey()));
  }

  @GetMapping("/channels/preference")
  public ApiResponse<PreferenceView> preference() {
    return ApiResponse.ok(channels.preference(SecurityConfig.currentAccount()));
  }

  @PutMapping("/channels/preference")
  public ApiResponse<PreferenceView> preference(@Valid @RequestBody PreferenceRequest request) {
    return ApiResponse.ok(
        channels.select(
            SecurityConfig.currentAccount(),
            new PreferenceCommand(
                request.personalChannelId(),
                request.confirmPlatform(),
                request.versionNo(),
                request.idempotencyKey())));
  }

  @PostMapping("/channels/personal/{channelId}/archive")
  public ApiResponse<SystemChannelView> archive(
      @PathVariable String channelId, @Valid @RequestBody VersionedCommandRequest request) {
    return ApiResponse.ok(
        channels.archive(
            SecurityConfig.currentAccount(),
            channelId,
            request.versionNo(),
            request.idempotencyKey()));
  }

  @GetMapping("/wallet")
  public ApiResponse<WalletView> wallet() {
    return ApiResponse.ok(billing.wallet(SecurityConfig.currentAccount()));
  }

  @GetMapping("/wallet/ledger")
  public ApiResponse<PageResponse<LedgerView>> ledger(
      @RequestParam(required = false) String type,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return ApiResponse.ok(
        PageResponse.from(
            billing.ledger(
                SecurityConfig.currentAccount(),
                type,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")))));
  }

  @GetMapping("/usage")
  public ApiResponse<PageResponse<UsageView>> usage(
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String from,
      @RequestParam(required = false) String to,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return ApiResponse.ok(
        PageResponse.from(
            billing.usage(
                SecurityConfig.currentAccount(),
                status,
                from,
                to,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "startedAt")))));
  }

  @GetMapping("/billing")
  public ApiResponse<PageResponse<BillingView>> billing(
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String from,
      @RequestParam(required = false) String to,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return ApiResponse.ok(
        PageResponse.from(
            billing.billing(
                SecurityConfig.currentAccount(),
                status,
                from,
                to,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")))));
  }

  @GetMapping("/subscriptions/personal-channel")
  public ApiResponse<SubscriptionView> subscription() {
    return ApiResponse.ok(billing.subscription(SecurityConfig.currentAccount()));
  }

  @PostMapping("/subscriptions/personal-channel")
  public ApiResponse<SubscriptionView> subscribe(@Valid @RequestBody SubscribeRequest request) {
    if (request.autoRenew())
      throw com.jobproof.shared.error.AppException.user("MANUAL_RENEWAL_ONLY", "个人通道仅支持手动续费");
    var account = SecurityConfig.currentAccount();
    purchases.purchase(
        account.accountId(),
        new com.jobproof.modules.aibilling.application.CommercePurchaseService.Purchase(
            "PERSONAL_CHANNEL", request.priceVersion(), request.idempotencyKey()));
    return ApiResponse.ok(billing.subscription(account));
  }

  @PostMapping("/subscriptions/personal-channel/cancel-renewal")
  public ApiResponse<SubscriptionView> cancelRenewal(
      @Valid @RequestBody VersionedCommandRequest request) {
    return ApiResponse.ok(
        billing.cancelRenewal(SecurityConfig.currentAccount(), request.versionNo()));
  }

  public record CreatePersonalChannelRequest(
      @NotBlank @Size(max = 128) String name,
      @Size(max = 64) String providerCode,
      @NotBlank @Size(max = 1024) String baseUrl,
      @NotBlank @Size(max = 4096) String apiKey,
      @NotBlank String protocol,
      @NotBlank @Size(max = 128) String idempotencyKey) {}

  public record UpdatePersonalChannelRequest(
      @NotBlank @Size(max = 128) String name,
      @Size(max = 64) String providerCode,
      @NotBlank @Size(max = 1024) String baseUrl,
      @Size(max = 4096) String apiKey,
      String protocol,
      @Size(max = 128) String selectedModel,
      String status,
      @Min(0) int versionNo,
      @NotBlank @Size(max = 128) String idempotencyKey) {}

  public record VersionedCommandRequest(
      @Min(0) int versionNo, @NotBlank @Size(max = 128) String idempotencyKey) {}

  public record TestChannelRequest(
      @Size(max = 128) String providerModel, @NotBlank @Size(max = 128) String idempotencyKey) {}

  public record ActivationRequest(
      @Min(0) int versionNo,
      @Size(max = 128) String providerModel,
      @NotBlank @Size(max = 128) String idempotencyKey) {}

  public record PreferenceRequest(
      @Size(max = 36) String personalChannelId,
      boolean confirmPlatform,
      @Min(0) int versionNo,
      @NotBlank @Size(max = 128) String idempotencyKey) {}

  public record SubscribeRequest(
      boolean autoRenew,
      @Min(0) int versionNo,
      @jakarta.validation.constraints.NotNull @Min(0) Integer priceVersion,
      @NotBlank @Size(max = 128) String idempotencyKey) {}

  public record PageResponse<T>(
      List<T> items, int page, int size, long totalElements, int totalPages) {
    static <T> PageResponse<T> from(Page<T> page) {
      return new PageResponse<>(
          page.getContent(),
          page.getNumber(),
          page.getSize(),
          page.getTotalElements(),
          page.getTotalPages());
    }
  }
}
