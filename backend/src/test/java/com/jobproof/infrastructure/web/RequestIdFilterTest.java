package com.jobproof.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestIdFilterTest {

    private final RequestIdFilter filter = new RequestIdFilter(1000);

    @Test
    void acceptsAWellFormedInboundIdAndExposesItDuringTheRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/me");
        request.addHeader(RequestIdFilter.HEADER, "client-trace-0001");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> seen = new AtomicReference<>();
        FilterChain chain = (req, res) -> seen.set(RequestIdFilter.current());

        filter.doFilter(request, response, chain);

        assertThat(seen.get()).isEqualTo("client-trace-0001");
        assertThat(response.getHeader(RequestIdFilter.HEADER)).isEqualTo("client-trace-0001");
        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).as("MDC is cleared after the request").isNull();
    }

    @Test
    void replacesMalformedOrMissingIds() throws Exception {
        for (String inbound : new String[] {null, "short", "has spaces in it", "x".repeat(65), "<script>alert(1)</script>"}) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/me");
            if (inbound != null) request.addHeader(RequestIdFilter.HEADER, inbound);
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, (req, res) -> { });

            String issued = response.getHeader(RequestIdFilter.HEADER);
            assertThat(issued).isNotEqualTo(inbound).matches("[A-Za-z0-9-]{8,64}");
        }
    }

    @Test
    void clearsTheMdcEvenWhenTheChainThrows() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/me");
        MockHttpServletResponse response = new MockHttpServletResponse();
        try {
            filter.doFilter(request, response, (req, res) -> {
                throw new IllegalStateException("boom");
            });
        } catch (Exception expected) {
            // propagated to the container
        }
        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
    }
}
