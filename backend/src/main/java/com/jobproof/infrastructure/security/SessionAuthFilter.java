package com.jobproof.infrastructure.security;

import com.jobproof.infrastructure.config.JobProofProperties;
import com.jobproof.modules.identity.application.IdentityService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class SessionAuthFilter extends OncePerRequestFilter {

    private final IdentityService identityService;
    private final JobProofProperties properties;

    public SessionAuthFilter(IdentityService identityService, JobProofProperties properties) {
        this.identityService = identityService;
        this.properties = properties;
    }

    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String token = readCookie(request, properties.getCookie().getName());
        identityService.authenticate(token).ifPresent(account -> {
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    account,
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + account.role())));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        });
        filterChain.doFilter(request, response);
    }

    public void writeSessionCookie(HttpServletResponse response, String rawToken, long maxAgeSeconds) {
        writeSessionCookie(null, response, rawToken, maxAgeSeconds);
    }

    public void writeSessionCookie(HttpServletRequest request, HttpServletResponse response, String rawToken, long maxAgeSeconds) {
        JobProofProperties.Cookie cookie = properties.getCookie();
        String sameSite = cookie.getSameSite();
        if (sameSite == null || sameSite.isBlank()) {
            sameSite = "Lax";
        }
        boolean secure = cookie.isSecure() || "None".equalsIgnoreCase(sameSite)
                || (request != null && request.isSecure());
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(cookie.getName(), rawToken)
                .httpOnly(true)
                .secure(secure)
                .path("/")
                .sameSite(sameSite);
        if (maxAgeSeconds >= 0) {
            builder.maxAge(maxAgeSeconds);
        }
        ResponseCookie built = builder.build();
        response.addHeader(HttpHeaders.SET_COOKIE, built.toString());
    }

    public void clearSessionCookie(HttpServletResponse response) {
        writeSessionCookie(response, "", 0);
    }

    public void clearSessionCookie(HttpServletRequest request, HttpServletResponse response) {
        writeSessionCookie(request, response, "", 0);
    }

    private static String readCookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
