package com.jobproof.modules.aibilling.web;

import com.jobproof.infrastructure.security.SecurityConfig;
import com.jobproof.infrastructure.web.ApiResponse;
import com.jobproof.modules.aibilling.application.*;
import com.jobproof.shared.auth.CurrentAccount;
import com.jobproof.shared.error.AppException;
import com.jobproof.shared.time.ClockPort;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api/v1/commerce")
public class CommerceController {
    private final CommerceCatalogService catalog;
    private final BalanceWalletService wallet;
    private final CommercePurchaseService purchase;
    private final AiUserBillingQueryService queries;
    private final JdbcTemplate jdbc;
    private final ClockPort clock;

    public CommerceController(
            CommerceCatalogService c,
            BalanceWalletService w,
            CommercePurchaseService p,
            AiUserBillingQueryService q,
            JdbcTemplate j,
            ClockPort clock) {
        catalog = c;
        wallet = w;
        purchase = p;
        queries = q;
        jdbc = j;
        this.clock = clock;
    }

    private CurrentAccount user() {
        var c = SecurityConfig.currentAccount();
        if (!Set.of("USER", "SEEKER", "ADMIN").contains(c.role()))
            throw AppException.forbidden("COMMERCE_FORBIDDEN", "仅求职者可使用钱包");
        return c;
    }

    private void admin() {
        if (!"ADMIN".equals(SecurityConfig.currentAccount().role()))
            throw AppException.forbidden("ADMIN_REQUIRED", "需要管理员权限");
    }

    @GetMapping("/catalog")
    public ApiResponse<?> catalog() {
        return ApiResponse.ok(Map.of("prices", catalog.prices(), "shop", catalog.settings()));
    }

    @GetMapping("/wallet")
    public ApiResponse<?> wallet() {
        return ApiResponse.ok(wallet.wallet(user().accountId()));
    }

    @GetMapping("/subscription")
    public ApiResponse<?> subscription() {
        return ApiResponse.ok(queries.subscription(user()));
    }

    @PostMapping("/purchase")
    public ApiResponse<?> purchase(@RequestBody CommercePurchaseService.Purchase p) {
        return ApiResponse.ok(purchase.purchase(user().accountId(), p));
    }

    @GetMapping("/history")
    public ApiResponse<?> history(
            @RequestParam(defaultValue = "ledger") String kind,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "0") int page) {
        return ApiResponse.ok(history(user().accountId(), kind, status, from, to, page));
    }

    @GetMapping("/admin/prices")
    public ApiResponse<?> prices() {
        admin();
        return ApiResponse.ok(catalog.prices());
    }

    @PutMapping("/admin/prices")
    public ApiResponse<?> price(@RequestBody CommerceCatalogService.ProductPrice p) {
        admin();
        return ApiResponse.ok(catalog.save(SecurityConfig.currentAccount(), p));
    }

    @PutMapping("/admin/shop")
    public ApiResponse<?> shop(@RequestBody CommerceCatalogService.ShopSettings s) {
        admin();
        return ApiResponse.ok(catalog.settings(SecurityConfig.currentAccount(), s));
    }

    @GetMapping("/admin/account/{id}")
    public ApiResponse<?> account(@PathVariable String id) {
        admin();
        return ApiResponse.ok(
                Map.of(
                        "wallet",
                        wallet.wallet(id),
                        "subscription",
                        queries.subscription(new CurrentAccount(id, "", "ADMIN", ""))));
    }

    @GetMapping("/admin/history")
    public ApiResponse<?> adminHistory(
            @RequestParam(required = false) String accountId,
            @RequestParam(defaultValue = "ledger") String kind,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "0") int page) {
        admin();
        return ApiResponse.ok(history(accountId, kind, status, from, to, page));
    }

    private Map<String, Object> history(
            String id, String kind, String status, Instant from, Instant to, int page) {
        if (page < 0 || page > 100000) throw AppException.user("PAGE_INVALID", "页码不合法");
        if (from != null && to != null && !from.isBefore(to)) throw AppException.user("DATE_RANGE_INVALID", "截止时间需要晚于开始时间");
        String table, date, statusCol, extra = "";
        List<Object> args = new ArrayList<>();
        switch (kind) {
            case "ledger" -> {
                table = "wallet_ledger t";
                date = "created_at";
                statusCol = "ledger_type";
                extra =
                        " AND EXISTS(SELECT 1 FROM wallet_account w WHERE w.id=t.wallet_account_id"
                                + " AND w.currency='CNY')";
            }
            case "bills" -> {
                table = "(SELECT b.*,u.model_code,u.payment_source,u.successful_units,u.input_tokens,u.output_tokens,p.name AS operation_name FROM ai_billing_record b LEFT JOIN ai_usage_record u ON u.id=b.usage_id LEFT JOIN commerce_prices p ON p.code=u.operation_code) t";
                date = "created_at";
                statusCol = "status";
            }
            case "orders" -> {
                table = "commerce_orders t";
                date = "created_at";
                statusCol = "product_code";
            }
            case "subscriptions" -> {
                // One clock value and one expression drive the list, filters and total count.
                table =
                        "(SELECT s.*, CASE WHEN s.status='ACTIVE' AND s.current_period_end<=? THEN"
                            + " 'EXPIRED' ELSE s.status END AS effective_status FROM"
                            + " personal_channel_subscription s) t";
                args.add(clock.now());
                date = "updated_at";
                statusCol = "effective_status";
            }
            case "pro-subscriptions" -> {
                table = "(SELECT m.*,m.account_id AS id,CASE WHEN m.plan='PRO' AND (m.expires_at IS NULL OR m.expires_at>?) THEN 'ACTIVE' WHEN m.plan='PRO' THEN 'EXPIRED' ELSE 'INACTIVE' END AS status FROM account_memberships m) t";
                args.add(clock.now());
                date = "updated_at";
                statusCol = "status";
            }
            case "wallets" -> {
                table = "wallet_account t";
                date = "updated_at";
                statusCol = "currency";
            }
            default -> throw AppException.user("HISTORY_KIND_INVALID", "查询类型无效");
        }
        String where = " WHERE 1=1" + extra;
        if (id != null && !id.isBlank()) {
            where += " AND t.account_id=?";
            args.add(id);
        }
        if (status != null && !status.isBlank()) {
            where += " AND t." + statusCol + "=?";
            args.add(status);
        }
        if (from != null) {
            where += " AND t." + date + ">=?";
            args.add(from);
        }
        if (to != null) {
            where += " AND t." + date + "<?";
            args.add(to);
        }
        Long total =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM " + table + where, Long.class, args.toArray());
        args.add(50);
        args.add(page * 50);
        var content =
                jdbc.queryForList(
                        "SELECT t.* FROM "
                                + table
                                + where
                                + " ORDER BY t."
                                + date
                                + " DESC,t.id DESC LIMIT ? OFFSET ?",
                        args.toArray());
        if ("subscriptions".equals(kind)) {
            for (Map<String, Object> row : content) {
                row.put("status", row.remove("effective_status"));
            }
        }
        return Map.of("content", content, "totalElements", total, "page", page, "size", 50);
    }
}
