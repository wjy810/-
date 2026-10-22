package com.jobproof.modules.aibilling.application;

import com.jobproof.modules.audit.application.AuditService;
import com.jobproof.shared.error.AppException;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.net.URI;
import java.util.List;

@Service
public class CommerceCatalogService {
    private final JdbcTemplate jdbc;
    private final AuditService audit;

    public CommerceCatalogService(JdbcTemplate jdbc, AuditService audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    public List<ProductPrice> prices() {
        return jdbc.query(
                "SELECT * FROM commerce_prices ORDER BY code",
                (r, n) ->
                        new ProductPrice(
                                r.getString("code"),
                                r.getString("name"),
                                r.getBigDecimal("amount"),
                                r.getString("currency"),
                                r.getInt("days"),
                                r.getBoolean("enabled"),
                                r.getInt("version")));
    }

    public ProductPrice requirePrice(String code) {
        return prices().stream()
                .filter(
                        p ->
                                p.code().equals(code)
                                        && p.enabled()
                                        && p.amount() != null
                                        && p.amount().signum() >= 0
                                        && "CNY".equals(p.currency()))
                .findFirst()
                .orElseThrow(
                        () -> AppException.conflict("COMMERCE_PRICE_UNAVAILABLE", "价格尚未配置或商品已停用"));
    }

    @Transactional
    public ProductPrice save(com.jobproof.shared.auth.CurrentAccount current, ProductPrice p) {
        admin(current);
        if (p == null
                || p.code() == null
                || p.name() == null
                || p.name().isBlank()
                || p.name().length() > 120
                || p.days() < 0
                || p.days() > 3650
                || !"CNY".equals(p.currency())
                || p.enabled() && p.amount() == null)
            throw AppException.user("COMMERCE_PRICE_INVALID", "请检查价格配置");
        if (p.amount() != null) {
            BalanceWalletService.amount(p.amount());
            if ((p.code().startsWith("PRO_") || p.code().equals("PERSONAL_CHANNEL"))
                    && (p.amount().stripTrailingZeros().scale() > 2
                            || p.amount().compareTo(new BigDecimal("999999.99")) > 0))
                throw AppException.user("COMMERCE_PRICE_INVALID", "商品价格最多两位小数，最高 999999.99 元");
        }
        if ((p.code().startsWith("PRO_") && p.days() < 1)
                || ("PERSONAL_CHANNEL".equals(p.code()) && p.days() != 30))
            throw AppException.user("COMMERCE_DAYS_INVALID", "个人通道固定 30 天，PRO 天数必须为正数");
        if (jdbc.update(
                        "UPDATE commerce_prices SET"
                            + " name=?,amount=?,currency='CNY',days=?,enabled=?,version=version+1"
                            + " WHERE code=? AND version=?",
                        p.name(),
                        p.amount(),
                        p.days(),
                        p.enabled(),
                        p.code(),
                        p.version())
                != 1) throw AppException.conflict("COMMERCE_VERSION_CONFLICT", "价格已更新，请刷新");
        audit.append(
                current.accountId(),
                "COMMERCE_PRICE_UPDATED",
                "COMMERCE_PRICE",
                p.code(),
                "价格更新："
                        + p.code()
                        + "，金额 "
                        + p.amount()
                        + " CNY，有效期 "
                        + p.days()
                        + " 天，上架 "
                        + p.enabled()
                        + "，版本 "
                        + p.version()
                        + " → "
                        + (p.version() + 1));
        return prices().stream().filter(x -> x.code().equals(p.code())).findFirst().orElseThrow();
    }

    public ShopSettings settings() {
        return jdbc.queryForObject(
                "SELECT * FROM commerce_settings WHERE id=1",
                (r, n) ->
                        new ShopSettings(
                                r.getBoolean("shop_enabled"),
                                r.getString("shop_url"),
                                r.getString("shop_name"),
                                r.getString("shop_description"),
                                r.getInt("version")));
    }

    @Transactional
    public ShopSettings settings(com.jobproof.shared.auth.CurrentAccount current, ShopSettings s) {
        admin(current);
        if (s == null) throw AppException.user("SHOP_SETTINGS_INVALID", "请填写购码入口设置");
        String name = s.name() == null ? "" : s.name().trim();
        String description = s.description() == null ? "" : s.description().trim();
        if (name.isEmpty() || name.length() > 80)
            throw AppException.user("SHOP_NAME_INVALID", "入口名称需要 1 到 80 个字");
        if (description.isEmpty() || description.length() > 500)
            throw AppException.user("SHOP_DESCRIPTION_INVALID", "入口说明需要 1 到 500 个字");
        String url = s.url() == null ? "" : s.url().trim();
        if (!url.isEmpty()) {
            try {
                URI u = URI.create(url);
                if (!"https".equalsIgnoreCase(u.getScheme())
                        || u.getHost() == null
                        || u.getUserInfo() != null
                        || url.length() > 2048) throw new IllegalArgumentException();
            } catch (IllegalArgumentException e) {
                throw AppException.user("SHOP_URL_INVALID", "购码链接必须是有效 HTTPS 地址");
            }
        }
        if (s.enabled() && url.isEmpty())
            throw AppException.user("SHOP_URL_REQUIRED", "启用前请填写购码链接");
        if (jdbc.update(
                        "UPDATE commerce_settings SET"
                            + " shop_enabled=?,shop_url=?,shop_name=?,shop_description=?,version=version+1"
                            + " WHERE id=1 AND version=?",
                        s.enabled(),
                        url,
                        name,
                        description,
                        s.version())
                != 1) throw AppException.conflict("COMMERCE_VERSION_CONFLICT", "设置已更新，请刷新");
        audit.append(
                current.accountId(),
                "COMMERCE_SHOP_UPDATED",
                "COMMERCE_SETTINGS",
                "1",
                "购码入口更新："
                        + name
                        + "，启用 "
                        + s.enabled()
                        + "，版本 "
                        + s.version()
                        + " → "
                        + (s.version() + 1));
        return settings();
    }

    private void admin(com.jobproof.shared.auth.CurrentAccount current) {
        if (current == null || !"ADMIN".equals(current.role()))
            throw AppException.forbidden("ADMIN_REQUIRED", "需要管理员权限");
    }

    public record ProductPrice(
            String code,
            String name,
            BigDecimal amount,
            String currency,
            int days,
            boolean enabled,
            int version) {}

    public record ShopSettings(
            boolean enabled, String url, String name, String description, int version) {}
}
