package com.jobproof.modules.identity.domain;

import com.jobproof.shared.error.AppException;
import java.util.Locale;
import java.util.regex.Pattern;

public final class AccountRules {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern CHINA_PHONE = Pattern.compile("^1[3-9]\\d{9}$");

    private AccountRules() {
    }

    public static String normalizeEmail(String raw) {
        if (raw == null || raw.isBlank()) {
            throw AppException.user("EMAIL_INVALID", "邮箱不能为空");
        }
        String email = raw.trim().toLowerCase(Locale.ROOT);
        if (email.length() > 320 || !EMAIL.matcher(email).matches()) {
            throw AppException.user("EMAIL_INVALID", "邮箱格式不正确");
        }
        return email;
    }

    public static void validatePassword(String password, String email) {
        if (password == null || password.length() < 8) {
            throw AppException.user("PASSWORD_TOO_WEAK", "密码至少 8 位");
        }
        if (password.length() > 72 || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
            throw AppException.user("PASSWORD_TOO_WEAK", "密码过长");
        }
        if (email != null && password.equalsIgnoreCase(email)) {
            throw AppException.user("PASSWORD_TOO_WEAK", "密码不能与邮箱相同");
        }
    }

    public static String normalizePhoneE164(String raw) {
        if (raw == null || raw.isBlank()) {
            throw AppException.user("PHONE_INVALID", "手机号不能为空");
        }
        String phone = raw.trim().replaceAll("[\\s()\\-]", "");
        if (phone.startsWith("0086")) phone = phone.substring(4);
        else if (phone.startsWith("+86")) phone = phone.substring(3);
        else if (phone.startsWith("86") && phone.length() == 13) phone = phone.substring(2);
        if (!CHINA_PHONE.matcher(phone).matches()) {
            throw AppException.user("PHONE_INVALID", "请输入正确的中国大陆手机号");
        }
        return "+86" + phone;
    }
}
