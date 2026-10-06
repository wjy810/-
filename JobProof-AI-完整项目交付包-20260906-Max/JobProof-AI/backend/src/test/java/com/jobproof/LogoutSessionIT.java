package com.jobproof;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LogoutSessionIT {

    @Autowired
    MockMvc mockMvc;

    @Test
    void logoutClearsCookieRevokesCurrentTokenAndKeepsOtherDevice() throws Exception {
        String email = "logout-multi+" + System.nanoTime() + "@example.com";
        register(email);

        Cookie deviceA = login(email, "Passw0rd!");
        Cookie deviceB = login(email, "Passw0rd!");
        assertThat(deviceA.getValue()).isNotBlank().isNotEqualTo(deviceB.getValue());

        mockMvc.perform(get("/api/v1/me").cookie(deviceA)).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/me").cookie(deviceB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(email));

        MvcResult logout = mockMvc.perform(post("/api/v1/auth/logout").cookie(deviceA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andReturn();

        String setCookie = String.join(";", logout.getResponse().getHeaders(HttpHeaders.SET_COOKIE));
        assertThat(setCookie).contains("jobproof_session=");
        assertThat(setCookie).contains("HttpOnly");
        assertThat(setCookie).containsIgnoringCase("SameSite=Lax");
        assertThat(setCookie).contains("Path=/");
        assertThat(parseMaxAge(setCookie)).isZero();
        Cookie cleared = logout.getResponse().getCookie("jobproof_session");
        assertThat(cleared).isNotNull();
        assertThat(cleared.getValue() == null || cleared.getValue().isBlank()).isTrue();

        mockMvc.perform(get("/api/v1/me").cookie(deviceA))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.error.category").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.error.reason").value("UNAUTHENTICATED"));

        mockMvc.perform(get("/api/v1/me").cookie(deviceB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(email));
    }

    @Test
    void logoutWithoutSessionDoesNotIssueALiveCookie() throws Exception {
        MvcResult logout = mockMvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.reason").value("UNAUTHENTICATED"))
                .andReturn();

        Cookie issued = logout.getResponse().getCookie("jobproof_session");
        if (issued != null) {
            assertThat(issued.getValue() == null || issued.getValue().isBlank()).isTrue();
            assertThat(issued.getMaxAge()).isZero();
        }
    }

    private void register(String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"Passw0rd!"}
                                """.formatted(email)))
                .andExpect(status().isOk());
    }

    private Cookie login(String email, String password) throws Exception {
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn();
        Cookie cookie = login.getResponse().getCookie("jobproof_session");
        assertThat(cookie).isNotNull();
        return cookie;
    }

    private static int parseMaxAge(String setCookie) {
        return Arrays.stream(setCookie.split(";"))
                .map(String::trim)
                .filter(part -> part.regionMatches(true, 0, "Max-Age=", 0, 8))
                .map(part -> Integer.parseInt(part.substring(8).trim()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Set-Cookie missing Max-Age: " + setCookie));
    }
}
