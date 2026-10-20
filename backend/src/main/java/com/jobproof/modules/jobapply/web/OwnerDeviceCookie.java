package com.jobproof.modules.jobapply.web;

import com.jobproof.modules.jobapply.application.ResumeLinkService;
import com.jobproof.shared.auth.CurrentAccount;
import com.jobproof.shared.auth.SignOutListener;
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
 * Marks a browser as the job seeker's own. Opening 投递追踪 gives the browser a random cookie and registers it for
 * the account, so when the owner later opens one of their links there without being logged in (to check how it
 * looks), the visit is not reported as an HR reading their resume. The mark lasts
 * {@link ResumeLinkService#OWNER_DEVICE_TTL} after the last visit (cookie and registration both), and signing
 * out removes it: a shared or borrowed computer stops hiding other people's openings.
 */
@Component
public class OwnerDeviceCookie implements SignOutListener {

    static final String NAME = "jp_owner_device";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ResumeLinkService links;

    public OwnerDeviceCookie(ResumeLinkService links) {
        this.links = links;
    }

    void register(CurrentAccount current, HttpServletRequest request, HttpServletResponse response) {
        if (current == null || current.operator()) return;
        String token = read(request, NAME);
        if (token == null) token = newToken();
        // Renewed on every visit, so it lapses only after a month without opening 投递追踪 in this browser.
        write(request, response, token, ResumeLinkService.OWNER_DEVICE_TTL);
        links.registerOwnerDevice(current.accountId(), token);
    }

    @Override
    public void signedOut(String accountId, HttpServletRequest request, HttpServletResponse response) {
        String token = read(request, NAME);
        if (token == null) return;
        links.forgetOwnerDevice(accountId, token);
        write(request, response, "", Duration.ZERO);
    }

    @Override
    public void otherBrowsersSignedOut(String accountId, boolean includingThis, HttpServletRequest request,
            HttpServletResponse response) {
        String token = read(request, NAME);
        links.forgetOtherOwnerDevices(accountId, includingThis ? null : token);
        if (includingThis && token != null) write(request, response, "", Duration.ZERO);
    }

    private static void write(HttpServletRequest request, HttpServletResponse response, String value, Duration maxAge) {
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(NAME, value).httpOnly(true)
                .secure(request.isSecure()).sameSite("Lax").path("/").maxAge(maxAge).build().toString());
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
