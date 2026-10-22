package com.jobproof.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobproof.modules.aigateway.port.AiTransportPort;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Deterministic external provider, shared by discovery and HTTP-to-gateway tests. */
public class AiProviderStub implements AiTransportPort {
    private final ObjectMapper json = new ObjectMapper();
    public List<String> models = List.of("embedding-only", "timed-out", "plain-only", "bad-json", "working-model", "unused-model");
    public final List<String> calls = new ArrayList<>();

    @Override
    public TransportResponse exchange(URI uri, String method, Map<String, String> headers, JsonNode body, Duration timeout) {
        if ("GET".equals(method)) {
            var discovery = json.createObjectNode();
            var data = discovery.putArray("data");
            models.forEach(model -> data.addObject().put("id", model));
            return new TransportResponse(200, Map.of(), discovery);
        }
        String model = body.path("model").asText();
        calls.add(model);
        if (model.equals("timed-out")) throw new IllegalStateException("simulated provider timeout");
        if (model.equals("embedding-only")) return new TransportResponse(400, Map.of(), json.createObjectNode());
        boolean structured = body.toString().contains("Return only this JSON object");
        String text = structured && !model.equals("bad-json") ? "{\"ok\":true}" : "OK";
        var response = json.createObjectNode().put("id", "test-response").put("model", model);
        response.putArray("choices").addObject().putObject("message").put("content", text);
        return new TransportResponse(200, Map.of(), response);
    }

    @Override
    public RawTransportResponse exchangeRaw(URI uri, String method, Map<String, String> headers, JsonNode body, Duration timeout) {
        String model = body.path("model").asText();
        calls.add(model);
        return new RawTransportResponse(200, Map.of(), model.equals("plain-only") ? "data: [DONE]\n\n"
                : "data: {\"choices\":[{\"delta\":{\"content\":\"OK\"}}]}\n\ndata: [DONE]\n\n");
    }

    @Override
    public StreamTransportResponse exchangeStream(URI uri, String method, Map<String, String> headers,
            JsonNode body, Duration timeout, Consumer<String> consumer) {
        String model = body.path("model").asText();
        calls.add(model);
        consumer.accept("{\"id\":\"test-response\",\"model\":\"" + model + "\",\"choices\":[{\"delta\":{\"content\":\"OK\"}}]}");
        consumer.accept("[DONE]");
        return new StreamTransportResponse(200, Map.of(), "");
    }
}
