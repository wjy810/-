package com.jobproof.modules.resume.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobproof.modules.resume.application.ResumeRenderPort;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class HttpResumeRenderClientTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String TOKEN = "test-renderer-token-0123456789";

    private HttpServer server;
    private final List<JsonNode> bodies = new CopyOnWriteArrayList<>();
    private final List<String> authorizations = new CopyOnWriteArrayList<>();

    @AfterEach
    void stop() {
        if (server != null) server.stop(0);
    }

    private String serve(int... statuses) throws Exception {
        AtomicInteger calls = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/render", exchange -> {
            bodies.add(MAPPER.readTree(exchange.getRequestBody().readAllBytes()));
            authorizations.add(exchange.getRequestHeaders().getFirst("Authorization"));
            int status = statuses[Math.min(calls.getAndIncrement(), statuses.length - 1)];
            byte[] body = status == 200 ? "%PDF-1.7 test".getBytes(StandardCharsets.US_ASCII)
                    : ("{\"code\":\"" + (status == 503 ? "BUSY" : "TEMPLATE_UNKNOWN") + "\"}").getBytes(StandardCharsets.UTF_8);
            if (status == 200) {
                exchange.getResponseHeaders().add("Content-Type", "application/pdf");
                exchange.getResponseHeaders().add("X-Page-Count", "2");
                exchange.getResponseHeaders().add("X-Overflow-Mm", "12.5");
                exchange.getResponseHeaders().add("X-Overflow-Section", "projects");
                exchange.getResponseHeaders().add("X-Render-Ms", "310");
            }
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/";
    }

    private static ResumeRenderPort.RenderRequest request() {
        return ResumeRenderPort.RenderRequest.pdf("classic", MAPPER.createObjectNode().put("paletteId", "ink"),
                MAPPER.createObjectNode().put("summary", "hello"), null, "简历", 9);
    }

    @Test
    void retriesBusyRendererAndReadsTheLayoutHeaders() throws Exception {
        HttpResumeRenderClient client = new HttpResumeRenderClient(MAPPER, serve(503, 200), TOKEN, 5000, 2);
        ResumeRenderPort.RenderedDocument document = client.render(request());
        assertThat(new String(document.body(), StandardCharsets.US_ASCII)).startsWith("%PDF");
        assertThat(document.pageCount()).isEqualTo(2);
        assertThat(document.overflowMm()).isEqualTo(12.5);
        assertThat(document.overflowSection()).isEqualTo("projects");
        assertThat(document.renderMs()).isEqualTo(310);
        assertThat(bodies).hasSize(2);
        assertThat(authorizations).containsOnly("Bearer " + TOKEN);
        JsonNode body = bodies.get(0);
        assertThat(body.path("format").asText()).isEqualTo("pdf");
        assertThat(body.path("payload").path("templateId").asText()).isEqualTo("classic");
        assertThat(body.path("payload").path("design").path("paletteId").asText()).isEqualTo("ink");
        assertThat(body.path("payload").has("photo")).isFalse();
        assertThat(body.path("options").path("pageLimit").asInt()).isEqualTo(5);
    }

    @Test
    void rejectedRequestsAreNotRetried() throws Exception {
        HttpResumeRenderClient client = new HttpResumeRenderClient(MAPPER, serve(422), TOKEN, 5000, 2);
        assertThatThrownBy(() -> client.render(request()))
                .isInstanceOfSatisfying(ResumeRenderPort.RenderFailedException.class, failure -> {
                    assertThat(failure.code()).isEqualTo("RENDERER_TEMPLATE_UNKNOWN");
                    assertThat(failure.retryable()).isFalse();
                });
        assertThat(bodies).hasSize(1);
    }

    @Test
    void outagesFailAsRetryableAfterTheConfiguredAttempts() throws Exception {
        HttpResumeRenderClient client = new HttpResumeRenderClient(MAPPER, serve(503), TOKEN, 5000, 1);
        assertThatThrownBy(() -> client.render(request()))
                .isInstanceOfSatisfying(ResumeRenderPort.RenderFailedException.class, failure -> {
                    assertThat(failure.code()).isEqualTo("RENDERER_UNAVAILABLE");
                    assertThat(failure.retryable()).isTrue();
                });
        assertThat(bodies).hasSize(2);
    }

    @Test
    void unconfiguredRendererFailsWithoutACall() {
        HttpResumeRenderClient client = new HttpResumeRenderClient(MAPPER, "", "", 5000, 2);
        assertThatThrownBy(() -> client.render(request()))
                .isInstanceOfSatisfying(ResumeRenderPort.RenderFailedException.class,
                        failure -> assertThat(failure.code()).isEqualTo("RENDERER_NOT_CONFIGURED"));
    }
}
