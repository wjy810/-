package com.jobproof;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
class SessionCookieSecurityIT {

    @Autowired
    MockMvc mockMvc;

    @Test
    void loginCookieIsHttpOnlySameSiteLaxAndMaxAgeFollowsSessionTtl() throws Exception {
        String email = "cookie+" + System.nanoTime() + "@example.com";
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"Passw0rd!"}
                                """.formatted(email)))
                .andExpect(status().isOk());

        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"Passw0rd!"}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn();

        Cookie session = login.getResponse().getCookie("jobproof_session");
        assertThat(session).isNotNull();
        assertThat(session.isHttpOnly()).isTrue();

        String setCookie = String.join(";", login.getResponse().getHeaders(HttpHeaders.SET_COOKIE));
        assertThat(setCookie).contains("jobproof_session=");
        assertThat(setCookie).contains("HttpOnly");
        assertThat(setCookie).containsIgnoringCase("SameSite=Lax");
        assertThat(setCookie).contains("Path=/");
        assertThat(Arrays.stream(setCookie.split(";")))
                .noneMatch(part -> part.trim().equalsIgnoreCase("Secure"));

        int maxAge = parseMaxAge(setCookie);
        assertThat(maxAge).isBetween(14 * 24 * 3600 - 10, 14 * 24 * 3600);
    }

    @Test
    void loginCookieIsBrowserSessionOnlyWhenRememberMeIsDisabled() throws Exception {
        String email = "session-cookie+" + System.nanoTime() + "@example.com";
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"Passw0rd!"}
                                """.formatted(email)))
                .andExpect(status().isOk());

        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"Passw0rd!","rememberMe":false}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn();

        Cookie session = login.getResponse().getCookie("jobproof_session");
        assertThat(session).isNotNull();
        assertThat(session.getMaxAge()).isEqualTo(-1);
        String setCookie = String.join(";", login.getResponse().getHeaders(HttpHeaders.SET_COOKIE));
        assertThat(setCookie).doesNotContainIgnoringCase("Max-Age=");
        assertThat(setCookie).doesNotContainIgnoringCase("Expires=");
    }

    @Test
    void loginCookieIsPersistentWhenRememberMeIsEnabled() throws Exception {
        String email = "remember-cookie+" + System.nanoTime() + "@example.com";
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"Passw0rd!"}
                                """.formatted(email)))
                .andExpect(status().isOk());

        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"Passw0rd!","rememberMe":true}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn();

        String setCookie = String.join(";", login.getResponse().getHeaders(HttpHeaders.SET_COOKIE));
        assertThat(parseMaxAge(setCookie)).isBetween(14 * 24 * 3600 - 10, 14 * 24 * 3600);
    }

    @Test
    void corsAllowsConfiguredOriginWithCredentialsAndDeniesOthers() throws Exception {
        mockMvc.perform(options("/api/v1/auth/login")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));

        mockMvc.perform(options("/api/v1/auth/login")
                        .header(HttpHeaders.ORIGIN, "https://evil.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    private static int parseMaxAge(String setCookie) {
        for (String part : setCookie.split(";")) {
            String trimmed = part.trim();
            if (trimmed.regionMatches(true, 0, "Max-Age=", 0, 8)) {
                return Integer.parseInt(trimmed.substring(8).trim());
            }
        }
        throw new AssertionError("Set-Cookie missing Max-Age: " + setCookie);
    }
}
