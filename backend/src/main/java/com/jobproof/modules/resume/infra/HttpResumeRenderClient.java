package com.jobproof.modules.resume.infra;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jobproof.infrastructure.web.RequestIdFilter;
import com.jobproof.modules.resume.application.ResumeRenderPort;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * HTTP client for the renderer service: bearer token, bounded timeout, retries only for outages
 * (connection failures, 503 busy, 5xx), request id passthrough for log correlation.
 */
@Component
public class HttpResumeRenderClient implements ResumeRenderPort {
    private static final Logger log = LoggerFactory.getLogger(HttpResumeRenderClient.class);

    private final ObjectMapper mapper;
    private final URI endpoint;
    private final String token;
    private final Duration timeout;
    private final int retries;
    private final HttpClient client;

    public HttpResumeRenderClient(ObjectMapper mapper,
            @Value("${jobproof.renderer.url:}") String url,
            @Value("${jobproof.renderer.token:}") String token,
            @Value("${jobproof.renderer.timeout-ms:20000}") long timeoutMs,
            @Value("${jobproof.renderer.retries:2}") int retries) {
        this.mapper = mapper;
        this.endpoint = url == null || url.isBlank() ? null : URI.create(url.replaceAll("/+$", "") + "/v1/render");
        this.token = token == null ? "" : token.trim();
        this.timeout = Duration.ofMillis(Math.max(1000, timeoutMs));
        this.retries = Math.max(0, Math.min(5, retries));
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    @Override
    public RenderedDocument render(RenderRequest request) {
        if (endpoint == null || token.isEmpty()) {
            throw new RenderFailedException("RENDERER_NOT_CONFIGURED", "排版服务未配置", true);
        }
        byte[] body = body(request);
        String requestId = MDC.get(RequestIdFilter.MDC_KEY);
        RenderFailedException last = null;
        for (int attempt = 0; attempt <= retries; attempt++) {
            if (attempt > 0) sleep(300L * attempt * attempt);
            try {
                HttpRequest.Builder builder = HttpRequest.newBuilder(endpoint)
                        .timeout(timeout)
                        .header("Authorization", "Bearer " + token)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofByteArray(body));
                if (requestId != null && !requestId.isBlank()) builder.header("X-Request-Id", requestId);
                HttpResponse<byte[]> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
                int status = response.statusCode();
                if (status == 200) return document(response);
                String code = errorCode(response.body());
                if (status == 503 || status >= 500) {
                    last = new RenderFailedException("RENDERER_UNAVAILABLE", "排版服务暂时不可用（" + status + " " + code + "）", true);
                    continue;
                }
                // 400 / 401 / 413 / 422: retrying cannot help.
                throw new RenderFailedException(status == 422 ? "RENDERER_TEMPLATE_UNKNOWN" : "RENDERER_REJECTED",
                        "排版服务拒绝了请求（" + status + " " + code + "）", false);
            } catch (IOException exception) {
                last = new RenderFailedException("RENDERER_UNAVAILABLE", "无法连接排版服务", true, exception);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new RenderFailedException("RENDERER_UNAVAILABLE", "排版请求被中断", true, exception);
            }
            log.warn("renderer attempt {} failed: {}", attempt + 1, last.getMessage());
        }
        throw last;
    }

    private byte[] body(RenderRequest request) {
        ObjectNode root = mapper.createObjectNode();
        root.put("format", request.format());
        ObjectNode payload = root.putObject("payload");
        payload.put("templateId", request.templateId());
        payload.set("design", request.design());
        payload.set("content", request.content() == null ? mapper.createObjectNode() : request.content());
        if (request.photo() != null) payload.put("photo", request.photo());
        if (request.title() != null) payload.put("title", request.title().length() > 200
                ? request.title().substring(0, 200) : request.title());
        ObjectNode options = root.putObject("options");
        options.put("pageLimit", Math.max(1, Math.min(5, request.pageLimit())));
        if (request.firstPageOnly()) options.put("firstPageOnly", true);
        try {
            return mapper.writeValueAsBytes(root);
        } catch (IOException exception) {
            throw new RenderFailedException("RENDERER_REJECTED", "排版请求无法序列化", false, exception);
        }
    }

    private static RenderedDocument document(HttpResponse<byte[]> response) {
        return new RenderedDocument(
                response.body(),
                response.headers().firstValue("Content-Type").orElse("application/pdf"),
                (int) number(response, "X-Page-Count"),
                number(response, "X-Overflow-Mm"),
                response.headers().firstValue("X-Overflow-Section").filter(value -> !value.isBlank()).orElse(null),
                (long) number(response, "X-Render-Ms"));
    }

    private static double number(HttpResponse<?> response, String header) {
        try {
            return Double.parseDouble(response.headers().firstValue(header).orElse("0"));
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private String errorCode(byte[] body) {
        try {
            JsonNode node = mapper.readTree(body);
            return node.path("code").asText("UNKNOWN");
        } catch (IOException | RuntimeException exception) {
            return "UNKNOWN";
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
