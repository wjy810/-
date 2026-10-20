package com.jobproof.modules.jobapply.web;

import com.jobproof.modules.jobapply.application.ResumeLinkService;
import com.jobproof.shared.auth.CurrentAccount;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * Marks a browser as the job seeker's own. Opening 投递追踪 gives the browser a long-lived random cookie and
 * registers it for the account, so when the owner later opens one of their links there without being logged in
 * (to check how it looks), the visit is not reported as an HR reading their resume.
 */
@Component
public class OwnerDeviceCookie {

    static final String NAME = "jp_owner_device";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ResumeLinkService links;

    public OwnerDeviceCookie(ResumeLinkService links) {
        this.links = links;
    }

    void register(CurrentAccount current, HttpServletRequest request, HttpServletResponse response) {
        if (current == null || current.operator()) return;
        String token = read(request, NAME);
        if (token == null) {
            token = newToken();
            response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(NAME, token).httpOnly(true)
                    .secure(request.isSecure()).sameSite("Lax").path("/").maxAge(Duration.ofDays(365)).build().toString());
        }
        links.registerOwnerDevice(current.accountId(), token);
    }

    static String read(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie cookie : cookies) {
            String value = cookie.getValue();
            if (name.equals(cookie.getName()) && value != null && value.matches("[A-Za-z0-9_-]{20,64}")) return value;
        }
        return null;
    }

    static String newToken() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
