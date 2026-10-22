package com.jobproof.modules.aigateway.application;
import com.fasterxml.jackson.databind.*;import com.jobproof.modules.aigateway.adapter.*;import com.jobproof.modules.aigateway.domain.AiGatewayModels.*;import com.jobproof.modules.aigateway.port.AiTransportPort;import com.jobproof.modules.aigateway.security.*;import java.net.URI;import java.time.Duration;import java.util.*;
public final class AiChannelInspector {
    private final AiTransportPort transport;
    private final ApiKeyCipher cipher;
    private final SsrfGuard ssrf;
    private final ObjectMapper json;

    public AiChannelInspector(AiTransportPort transport, ApiKeyCipher cipher, SsrfGuard ssrf, ObjectMapper json) {
        this.transport = transport;
        this.cipher = cipher;
        this.ssrf = ssrf;
        this.json = json;
    }

    public Detection inspect(Channel channel) {
        return inspect(channel, Duration.ofSeconds(10));
    }

    private Detection inspect(Channel channel, Duration timeout) {
        try {
            ssrf.validate(channel.baseUri());
            AiProtocolAdapter adapter = switch (channel.protocol()) {
                case OPENAI_CHAT -> new ProtocolAdapters.OpenAiChat(json);
                case OPENAI_RESPONSES -> new ProtocolAdapters.OpenAiResponses(json);
                case ANTHROPIC_MESSAGES -> new ProtocolAdapters.AnthropicMessages(json);
            };
            var uri = adapter.modelsUri(channel.baseUri());
            ssrf.validate(uri);
            var response = transport.exchange(uri, "GET",
                adapter.authHeaders(cipher.decrypt(channel.encryptedApiKey())), null, timeout);
            if (response.status() == 401 || response.status() == 403) {
                return new Detection(true, false, List.of(), "Authentication rejected");
            }
            if (response.status() < 200 || response.status() >= 300) {
                return new Detection(false, false, List.of(), "HTTP " + response.status());
            }
            return new Detection(true, true, models(response.body()), "OK");
        } catch (RuntimeException e) {
            return new Detection(false, false, List.of(), e.getMessage());
        }
    }

    public CapabilityDetection inspectCapabilities(Channel channel, String requestedModel) {
        boolean automatic = requestedModel == null || requestedModel.isBlank();
        String requested = automatic ? null : requestedModel.trim();
        long deadline = System.nanoTime() + Duration.ofSeconds(120).toNanos();
        CapabilityDetection last = failed(channel.baseUri(), List.of(), requested, false, false,
                false, false, false, "ENDPOINT_DETECTION_FAILED", "未发现可用的鉴权和模型列表接口");
        int attempted = 0;
        for (URI base : baseCandidates(channel.baseUri())) {
            if (System.nanoTime() >= deadline) break;
            Channel candidate = new Channel(channel.id(), channel.protocol(), base, channel.encryptedApiKey(),
                    channel.healthPriority(), channel.weight(), channel.enabled());
            Detection discovery = inspect(candidate, Duration.ofNanos(Math.min(
                    Math.max(1, deadline - System.nanoTime()), Duration.ofSeconds(10).toNanos())));
            if (!discovery.authenticated()) continue;
            if (discovery.models().isEmpty()) {
                last = failed(base, List.of(), null, true, false, false, false, false,
                        "MODEL_DISCOVERY_EMPTY", "模型发现成功但没有返回可用模型");
                continue;
            }
            if (!automatic && !discovery.models().contains(requested)) {
                last = failed(base, discovery.models(), requested, true, true, false, false, false,
                        "MODEL_NOT_DISCOVERED", "指定模型不在供应商发现列表中");
                continue;
            }
            List<String> candidates = automatic ? discovery.models() : List.of(requested);
            for (String model : candidates) {
                if (System.nanoTime() >= deadline) break;
                attempted++;
                last = probe(channel, base, discovery.models(), model, deadline);
                if (last.passed() || !automatic) return last;
            }
        }
        boolean timedOut = System.nanoTime() >= deadline;
        String detail = timedOut ? "检测达到 120 秒上限；可填写模型 ID 后单独测试。"
                : automatic && attempted > 0 ? "已测试 " + attempted + " 个模型，暂未找到全部能力通过的模型。" : "";
        return new CapabilityDetection(last.normalizedBaseUrl(), last.models(), last.testedModel(),
                last.authenticated(), last.modelDiscovery(), last.plainResponse(), last.streamingResponse(),
                last.structuredResponse(), timedOut ? "CAPABILITY_TEST_TIMEOUT" : last.failureCode(), detail + last.detail());
    }

    private CapabilityDetection probe(Channel channel, URI base, List<String> models, String model, long deadline) {
        boolean plainPassed = false;
        boolean streamPassed = false;
        String code = "PLAIN_RESPONSE_FAILED";
        String detail = "普通响应测试失败";
        try {
            AiProtocolAdapter adapter = adapter(channel.protocol());
            String key = cipher.decrypt(channel.encryptedApiKey());
            Request plain = new Request(model, List.of(new Message("user", "Reply with exactly OK")), false, Map.of());
            var outgoing = adapter.adapt(base, key, plain);
            ssrf.validate(outgoing.uri());
            var response = transport.exchange(outgoing.uri(), "POST", outgoing.headers(), outgoing.body(), timeout(deadline));
            plainPassed = successful(response.status()) && !adapter.parse(response.body()).text().isBlank();
            if (!plainPassed) return failed(base, models, model, true, true, false, false, false, code, detail);

            code = "STREAM_RESPONSE_FAILED";
            detail = "流式响应测试失败，需要有效正文及完整结束事件";
            var stream = adapter.adapt(base, key, new Request(model, plain.messages(), true, Map.of()));
            ssrf.validate(stream.uri());
            var streamed = transport.exchangeRaw(stream.uri(), "POST", stream.headers(), stream.body(), timeout(deadline));
            streamPassed = successful(streamed.status()) && completeStream(adapter, streamed.body());
            if (!streamPassed) return failed(base, models, model, true, true, true, false, false, code, detail);

            code = "STRUCTURED_RESPONSE_FAILED";
            detail = "结构化输出测试失败";
            var structured = adapter.adapt(base, key, new Request(model,
                    List.of(new Message("user", "Return only this JSON object: {\"ok\":true}")), false, Map.of()));
            ssrf.validate(structured.uri());
            var structuredHttp = transport.exchange(structured.uri(), "POST", structured.headers(), structured.body(), timeout(deadline));
            if (successful(structuredHttp.status())) {
                String text = adapter.parse(structuredHttp.body()).text().trim().replace("```json", "").replace("```", "").trim();
                JsonNode parsed = json.readTree(text);
                if (parsed != null && parsed.isObject() && parsed.path("ok").asBoolean(false)) {
                    return new CapabilityDetection(base.toString(), models, model, true, true, true, true, true, null,
                            "全部能力测试通过，检测模型：" + model);
                }
            }
        } catch (Exception ignored) {
            // A timeout, malformed reply or unsupported model must not prevent testing the next model.
        }
        return failed(base, models, model, true, true, plainPassed, streamPassed, false, code, detail);
    }

    private static boolean successful(int status) { return status >= 200 && status < 300; }

    private static Duration timeout(long deadline) {
        long remaining = deadline - System.nanoTime();
        if (remaining <= 0) throw new IllegalStateException("Capability test deadline exceeded");
        return Duration.ofNanos(Math.min(remaining, Duration.ofSeconds(30).toNanos()));
    }

    private static boolean completeStream(AiProtocolAdapter adapter, String body) {
        boolean content = false;
        for (String block : body.replace("\r\n", "\n").split("\n\n")) {
            String data = block.lines().filter(line -> line.startsWith("data:"))
                    .map(line -> line.substring(5).stripLeading()).collect(java.util.stream.Collectors.joining("\n"));
            if (data.isEmpty()) continue;
            var event = adapter.parseStreamData(data);
            if (event.done()) return content;
            if (event.delta() != null && !event.delta().isBlank()) content = true;
        }
        return false;
    }

    private CapabilityDetection failed(URI base,List<String> models,String model,boolean auth,boolean discovery,
            boolean plain,boolean stream,boolean structured,String code,String detail){
        return new CapabilityDetection(base.toString(),models,model,auth,discovery,plain,stream,structured,code,detail);
    }

    private AiProtocolAdapter adapter(com.jobproof.modules.aigateway.domain.AiProtocol protocol){
        return switch(protocol){
            case OPENAI_CHAT -> new ProtocolAdapters.OpenAiChat(json);
            case OPENAI_RESPONSES -> new ProtocolAdapters.OpenAiResponses(json);
            case ANTHROPIC_MESSAGES -> new ProtocolAdapters.AnthropicMessages(json);
        };
    }

    private static List<URI> baseCandidates(URI value){
        String raw=value.toString().replaceAll("/+$","");
        if(raw.endsWith("/v1"))return List.of(URI.create(raw));
        return List.of(URI.create(raw+"/v1"),URI.create(raw));
    }

    private List<String> models(JsonNode body) {
        List<String> result = new ArrayList<>();
        JsonNode data = body.path("data");
        if (!data.isArray()) {
            data = body.path("models");
        }
        if (data.isArray()) {
            for (JsonNode item : data) {
                String id = item.isTextual() ? item.asText() : item.path("id").asText();
                if (!id.isBlank() && id.trim().length() <= 128 && !result.contains(id.trim())) {
                    result.add(id.trim());
                }
            }
        }
        return List.copyOf(result);
    }
}
