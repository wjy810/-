package com.jobproof;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jobproof.modules.audit.infra.AuditEventJpaRepository;
import com.jobproof.shared.security.Tokens;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest(properties = {"jobproof.proxy.trusted-proxies=172.31.247.3/32",
        "jobproof.login-rate-limit.account-limit=2", "jobproof.login-rate-limit.ip-limit=3"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthSecurityIT {
    @Autowired MockMvc mvc;
    @Autowired AuditEventJpaRepository audits;

    @Test
    void asyncDispatchStillAuthenticatesTheSessionCookie() throws Exception {
        mvc.perform(get("/api/v1/me").with(request -> {
                    request.setDispatcherType(jakarta.servlet.DispatcherType.ASYNC); return request;
                })).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"async-check@example.com\",\"password\":\"WrongPass1!\"}"))
                .andExpect(status().isOk());
        var authenticated = mvc.perform(login("async-check@example.com", "198.51.100.70"))
                .andExpect(status().isOk()).andReturn();
        var cookie = authenticated.getResponse().getCookie("jobproof_session");
        assertThat(cookie).isNotNull();
        assertThat(authenticated.getResponse().getHeader("Set-Cookie")).contains("Secure");
        mvc.perform(get("/api/v1/me").cookie(cookie).with(request -> {
                    request.setDispatcherType(jakarta.servlet.DispatcherType.ASYNC); return request;
                })).andExpect(status().isOk()).andExpect(jsonPath("$.data.email").value("async-check@example.com"));
    }

    @Test
    void containerErrorDispatchRetainsOriginalErrorInsteadOfWritingAnAuthenticationError() throws Exception {
        mvc.perform(get("/error").with(request -> {
                    request.setDispatcherType(jakarta.servlet.DispatcherType.ERROR);
                    request.setAttribute(jakarta.servlet.RequestDispatcher.ERROR_STATUS_CODE, 500);
                    return request;
                }))
                .andExpect(status().isInternalServerError());
        mvc.perform(get("/error")).andExpect(status().isUnauthorized());
    }

    @Test
    void committedResponseErrorIncludeDoesNotReauthorizeTheErrorPage() throws Exception {
        mvc.perform(get("/error").with(request -> {
                    request.setDispatcherType(jakarta.servlet.DispatcherType.INCLUDE);
                    request.setAttribute(jakarta.servlet.RequestDispatcher.INCLUDE_REQUEST_URI, "/error");
                    request.setAttribute(jakarta.servlet.RequestDispatcher.ERROR_STATUS_CODE, 500);
                    return request;
                }))
                .andExpect(status().isInternalServerError());
        mvc.perform(get("/api/v1/me").with(request -> {
                    request.setDispatcherType(jakarta.servlet.DispatcherType.INCLUDE);
                    request.setAttribute(jakarta.servlet.RequestDispatcher.INCLUDE_REQUEST_URI, "/api/v1/me");
                    return request;
                }))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/error").header("jakarta.servlet.include.request_uri", "/error"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void proxyClientsHaveSeparateBudgetsButAccountBudgetSurvivesIpChanges() throws Exception {
        mvc.perform(login("limited@example.com", "198.51.100.1")).andExpect(status().isBadRequest());
        mvc.perform(login("limited@example.com", "198.51.100.2")).andExpect(status().isBadRequest());
        mvc.perform(login("LIMITED@example.com", "198.51.100.3"))
                .andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.error.reason").value("LOGIN_RATE_LIMITED"));
        mvc.perform(login("other@example.com", "198.51.100.3")).andExpect(status().isBadRequest());
        assertThat(audits.findAll().stream().filter(event -> "ACCOUNT_LOGIN_FAILED".equals(event.getAction())
                && Tokens.sha256("limited@example.com").equals(event.getObjectId())).count()).isEqualTo(2);
    }

    @Test
    void arbitraryForwardedHeaderCannotRotateIpBudget() throws Exception {
        for (int i = 0; i < 3; i++) {
            mvc.perform(login("direct-" + i + "@example.com", "198.51.100." + (20 + i))
                    .with(request -> { request.setRemoteAddr("203.0.113.88"); return request; }))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(login("direct-blocked@example.com", "198.51.100.99")
                .with(request -> { request.setRemoteAddr("203.0.113.88"); return request; }))
                .andExpect(status().isTooManyRequests());
    }

    private MockHttpServletRequestBuilder login(String identifier, String client) {
        return post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .header("X-Forwarded-For", client).header("X-Forwarded-Proto", "https")
                .with(request -> { request.setRemoteAddr("172.31.247.3"); return request; })
                .content("{\"identifier\":\"" + identifier + "\",\"password\":\"WrongPass1!\"}");
    }
}
