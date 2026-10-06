package com.jobproof.infrastructure.security;

import com.jobproof.infrastructure.config.TrustedProxyProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.InetAddress;
import java.util.List;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Only this filter interprets forwarding headers; native/framework forwarding must remain disabled. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class TrustedProxyFilter extends OncePerRequestFilter {
    private final List<IpAddressMatcher> trusted;

    public TrustedProxyFilter(TrustedProxyProperties properties) {
        trusted = properties.getTrustedProxies().stream().map(String::trim).filter(s -> !s.isEmpty())
                .map(cidr -> {
                    if (literal(cidr.split("/", -1)[0]) == null) {
                        throw new IllegalArgumentException("Trusted proxies must be literal IP addresses or CIDRs");
                    }
                    return new IpAddressMatcher(cidr);
                }).toList();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String peer = literal(request.getRemoteAddr());
        String forwarded = request.getHeader("X-Forwarded-For");
        if (peer == null || !isTrusted(peer) || forwarded == null || forwarded.length() > 2048) {
            chain.doFilter(request, response);
            return;
        }
        String[] hops = forwarded.split(",", -1);
        if (hops.length > 32) {
            chain.doFilter(request, response);
            return;
        }
        for (String hop : hops) {
            if (literal(hop.trim()) == null) {
                chain.doFilter(request, response);
                return;
            }
        }
        String client = peer;
        for (int i = hops.length - 1; i >= 0 && isTrusted(client); i--) {
            client = literal(hops[i].trim());
        }
        final String clientIp = client;
        // A trusted ingress must overwrite this header, never append untrusted values.
        boolean secure = request.isSecure() || "https".equalsIgnoreCase(request.getHeader("X-Forwarded-Proto"));
        chain.doFilter(new HttpServletRequestWrapper(request) {
            @Override public String getRemoteAddr() { return clientIp; }
            @Override public String getRemoteHost() { return clientIp; }
            @Override public boolean isSecure() { return secure; }
            @Override public String getScheme() { return secure ? "https" : super.getScheme(); }
        }, response);
    }

    private boolean isTrusted(String ip) { return trusted.stream().anyMatch(matcher -> matcher.matches(ip)); }

    private static String literal(String value) {
        if (value == null || value.length() > 45 || !value.matches("[0-9a-fA-F:.]+")) return null;
        // No DNS names, zone IDs, brackets, ports, or abbreviated IPv4 addresses.
        if (!value.contains(":")) {
            String[] parts = value.split("\\.", -1);
            if (parts.length != 4) return null;
            for (String part : parts) {
                if (!part.matches("0|[1-9][0-9]{0,2}") || Integer.parseInt(part) > 255) return null;
            }
        }
        try { return InetAddress.getByName(value).getHostAddress(); }
        catch (Exception ignored) { return null; }
    }
}
