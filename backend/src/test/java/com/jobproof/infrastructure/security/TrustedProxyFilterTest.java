package com.jobproof.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import com.jobproof.infrastructure.config.TrustedProxyProperties;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class TrustedProxyFilterTest {
    @Test
    void malformedForwardedChainsAndCommaSeparatedProtoNeverBecomeTrusted() throws Exception {
        var properties = new TrustedProxyProperties();
        properties.setTrustedProxies(List.of("172.31.247.3"));
        for (String header : List.of("localhost", "198.51.100.21,", "198.51.100.21:123", "127.1", "999.2.3.4")) {
            var request = new MockHttpServletRequest();
            request.setRemoteAddr("172.31.247.3");
            request.addHeader("X-Forwarded-For", header);
            request.addHeader("X-Forwarded-Proto", "https");
            new TrustedProxyFilter(properties).doFilter(request, new MockHttpServletResponse(), (req, res) -> {
                assertThat(req.getRemoteAddr()).isEqualTo("172.31.247.3");
                assertThat(req.isSecure()).isFalse();
            });
        }
        var request = new MockHttpServletRequest();
        request.setRemoteAddr("172.31.247.3");
        request.addHeader("X-Forwarded-For", "198.51.100.21");
        request.addHeader("X-Forwarded-Proto", "https,http");
        new TrustedProxyFilter(properties).doFilter(request, new MockHttpServletResponse(),
                (req, res) -> assertThat(req.isSecure()).isFalse());
    }
    @Test
    void arbitraryAndLoopbackClientsCannotForgeTheirIpOrSecureCookie() throws Exception {
        for (String peer : List.of("203.0.113.4", "127.0.0.1", "172.31.247.3")) {
            var request = new MockHttpServletRequest();
            request.setRemoteAddr(peer);
            request.addHeader("X-Forwarded-For", "1.1.1.1");
            request.addHeader("X-Forwarded-Proto", "https");
            var actual = new AtomicReference<HttpServletRequest>();
            new TrustedProxyFilter(new TrustedProxyProperties()).doFilter(request, new MockHttpServletResponse(),
                    (req, res) -> actual.set((HttpServletRequest) req));
            assertThat(actual.get().getRemoteAddr()).isEqualTo(peer);
            var cookies = new com.jobproof.infrastructure.config.JobProofProperties();
            cookies.getCookie().setSecure(false);
            var response = new MockHttpServletResponse();
            new SessionAuthFilter(null, cookies).writeSessionCookie(actual.get(), response, "fixture", 60);
            assertThat(response.getHeader("Set-Cookie")).doesNotContain("Secure");
        }
    }

    @Test
    void trustedProxySeparatesClientsAndStopsAtFirstUntrustedHop() throws Exception {
        var properties = new TrustedProxyProperties();
        properties.setTrustedProxies(List.of("172.31.247.3/32"));
        var request = new MockHttpServletRequest();
        request.setRemoteAddr("172.31.247.3");
        request.addHeader("X-Forwarded-For", "1.1.1.1, 198.51.100.21");
        request.addHeader("X-Forwarded-Proto", "https");
        var actual = new AtomicReference<HttpServletRequest>();
        new TrustedProxyFilter(properties).doFilter(request, new MockHttpServletResponse(),
                (req, res) -> actual.set((HttpServletRequest) req));
        assertThat(actual.get().getRemoteAddr()).isEqualTo("198.51.100.21");
        assertThat(actual.get().isSecure()).isTrue();
        var cookies = new com.jobproof.infrastructure.config.JobProofProperties();
        cookies.getCookie().setSecure(false);
        var response = new MockHttpServletResponse();
        new SessionAuthFilter(null, cookies).writeSessionCookie(actual.get(), response, "fixture", 60);
        assertThat(response.getHeader("Set-Cookie")).contains("Secure");
    }
}
