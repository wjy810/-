package com.jobproof.infrastructure.security;

import com.jobproof.infrastructure.config.JobProofProperties;
import com.jobproof.infrastructure.web.RequestIdFilter;
import com.jobproof.shared.auth.CurrentAccount;
import com.jobproof.shared.error.AppException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class SecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, SessionAuthFilter sessionAuthFilter) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(jakarta.servlet.DispatcherType.ERROR).permitAll()
                        .requestMatchers(SecurityConfig::isContainerErrorInclude).permitAll()
                        .requestMatchers("/api/v1/health", "/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers("/api/v1/auth/register", "/api/v1/auth/login").permitAll()
                        .requestMatchers("/api/v1/auth/verifications/**").permitAll()
                        .requestMatchers("/api/v1/auth/password/reset/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/template-catalog", "/api/v1/template-catalog/facets",
                                "/api/v1/template-catalog/*", "/api/v1/template-catalog/*/thumbnail",
                                "/api/v1/template-catalog/*/preview-pages/*").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/job-taxonomy").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/capabilities", "/api/v1/policies/*").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/updates", "/api/v1/updates/**").permitAll()
                        .requestMatchers("/internal/dev/**").permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(sessionAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) -> write(res, 401, "UNAUTHENTICATED", "未登录或会话已失效"))
                        .accessDeniedHandler((req, res, ex) -> write(res, 403, "FORBIDDEN", "没有权限")));
        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(JobProofProperties properties) {
        List<String> origins = properties.getCors().getOrigins().stream()
                .map(origin -> origin == null ? "" : origin.trim())
                .filter(origin -> !origin.isEmpty())
                .toList();
        if (origins.stream().anyMatch("*"::equals)) {
            throw new IllegalStateException("CORS origins cannot be '*' when credentials are enabled");
        }
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(origins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    public static CurrentAccount currentAccount() {
        CurrentAccount account = currentAccountOrNull();
        if (account == null) {
            throw AppException.unauthenticated();
        }
        return account;
    }

    public static CurrentAccount currentAccountOrNull() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof CurrentAccount account)) {
            return null;
        }
        return account;
    }

    private static void write(jakarta.servlet.http.HttpServletResponse res, int status, String reason, String message)
            throws java.io.IOException {
        res.setStatus(status);
        res.setCharacterEncoding("UTF-8");
        res.setContentType("application/json;charset=UTF-8");
        // requestId is either validated against [A-Za-z0-9-] or a generated UUID, so it is safe to embed.
        String requestId = RequestIdFilter.current();
        res.getWriter().write("{\"ok\":false,\"error\":{\"category\":\""
                + (status == 401 ? "UNAUTHENTICATED" : "FORBIDDEN")
                + "\",\"reason\":\"" + reason + "\",\"message\":\"" + message + "\""
                + (requestId == null ? "" : ",\"requestId\":\"" + requestId + "\"") + "}}");
    }

    public static boolean isSafeMethod(HttpServletRequest request) {
        return HttpMethod.GET.matches(request.getMethod())
                || HttpMethod.HEAD.matches(request.getMethod())
                || HttpMethod.OPTIONS.matches(request.getMethod());
    }

    private static boolean isContainerErrorInclude(HttpServletRequest request) {
        // Tomcat includes the error page after an async response has already been committed.
        return request.getDispatcherType() == jakarta.servlet.DispatcherType.INCLUDE
                && (request.getContextPath() + "/error").equals(
                        request.getAttribute(jakarta.servlet.RequestDispatcher.INCLUDE_REQUEST_URI))
                && request.getAttribute(jakarta.servlet.RequestDispatcher.ERROR_STATUS_CODE) instanceof Integer;
    }
}
