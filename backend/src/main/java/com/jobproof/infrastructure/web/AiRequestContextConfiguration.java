package com.jobproof.infrastructure.web;

import com.jobproof.shared.ai.AiExecutionContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.AsyncHandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class AiRequestContextConfiguration implements WebMvcConfigurer {
    @Override public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new AsyncHandlerInterceptor() {
            @Override public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                AiExecutionContext.set(new AiExecutionContext.State(request.getHeader("X-Ai-Quote"),
                        request.getRequestURI(), hash(new byte[0]), "true".equals(request.getHeader("X-Ai-Payment-Confirmed")),
                        null, null, null, null, request.getHeader("X-Ai-Operation-Id")));
                return true;
            }
            @Override public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception e) {
                AiExecutionContext.clear();
            }
            @Override public void afterConcurrentHandlingStarted(HttpServletRequest request, HttpServletResponse response, Object handler) {
                AiExecutionContext.clear();
            }
        }).addPathPatterns("/api/v1/**");
    }
    static String hash(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
