package com.jobproof.infrastructure.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Correlates one request across logs, error bodies and the client (docs/03 §5.8).
 * Accepts a well-formed inbound {@code X-Request-Id}, otherwise generates one; runs before security
 * so that rejected requests are traceable too.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";
    public static final String MDC_KEY = "requestId";
    private static final Pattern VALID = Pattern.compile("[A-Za-z0-9-]{8,64}");
    private static final Logger log = LoggerFactory.getLogger(RequestIdFilter.class);

    private final long slowRequestMs;

    public RequestIdFilter(@Value("${jobproof.observability.slow-request-ms:1000}") long slowRequestMs) {
        this.slowRequestMs = slowRequestMs;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId = resolve(request.getHeader(HEADER));
        MDC.put(MDC_KEY, requestId);
        response.setHeader(HEADER, requestId);
        long started = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } finally {
            long elapsedMs = (System.nanoTime() - started) / 1_000_000;
            // Streaming endpoints stay open by design; only plain requests count as slow.
            if (elapsedMs > slowRequestMs && !isStreaming(request)) {
                log.warn("slow request method={} path={} status={} durationMs={}",
                        request.getMethod(), request.getRequestURI(), response.getStatus(), elapsedMs);
            }
            MDC.remove(MDC_KEY);
        }
    }

    static String resolve(String inbound) {
        return inbound != null && VALID.matcher(inbound).matches() ? inbound : UUID.randomUUID().toString();
    }

    private static boolean isStreaming(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String accept = request.getHeader("Accept");
        return uri.endsWith("/events") || uri.endsWith("/stream") || (accept != null && accept.contains("text/event-stream"));
    }

    /** The current request id, or {@code null} outside a request (workers, tests). */
    public static String current() {
        return MDC.get(MDC_KEY);
    }
}
