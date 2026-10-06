package com.jobproof.modules.aigateway.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobproof.modules.aigateway.domain.AiGatewayModels.Channel;
import com.jobproof.modules.aigateway.domain.AiGatewayModels.Message;
import com.jobproof.modules.aigateway.domain.AiGatewayModels.Request;
import com.jobproof.modules.aigateway.domain.AiGatewayModels.Response;
import com.jobproof.modules.aigateway.domain.AiProtocol;
import com.jobproof.modules.aigateway.port.AiChannelPort;
import com.jobproof.modules.aigateway.port.AiTransportPort;
import com.jobproof.modules.aigateway.security.ApiKeyCipher;
import com.jobproof.modules.aigateway.security.SsrfGuard;
import java.net.InetAddress;
import java.net.URI;
import java.time.Duration;
import java.util.Base64;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Consumer;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class AiGatewayServiceTest {

    @Test
    void streamsOpenAiChatDeltasInOrderAndStopsAtDone() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ApiKeyCipher cipher = new ApiKeyCipher(Base64.getEncoder().encodeToString(new byte[32]));
        Channel mapped = new Channel("channel-1", AiProtocol.OPENAI_CHAT,
                URI.create("https://provider.example/v1"), cipher.encrypt("test-key"),
                "gpt-5.6-sol", 0, 100, true);
        List<String> deltas = new ArrayList<>();
        AtomicReference<JsonNode> outgoingBody = new AtomicReference<>();

        AiTransportPort transport = new AiTransportPort() {
            @Override
            public TransportResponse exchange(URI uri, String method, Map<String, String> headers,
                    JsonNode body, Duration timeout) {
                throw new AssertionError("Streaming must not use the buffered transport");
            }

            @Override
            public StreamTransportResponse exchangeStream(URI uri, String method, Map<String, String> headers,
                    JsonNode body, Duration timeout, Consumer<String> dataConsumer) {
                outgoingBody.set(body);
                dataConsumer.accept("{\"id\":\"chat-1\",\"model\":\"gpt-5.6-sol\",\"choices\":[{\"delta\":{\"content\":\"你\"}}]}");
                dataConsumer.accept("{\"id\":\"chat-1\",\"model\":\"gpt-5.6-sol\",\"choices\":[{\"delta\":{\"content\":\"好\"}}]}");
                dataConsumer.accept("{\"id\":\"chat-1\",\"model\":\"gpt-5.6-sol\",\"choices\":[],\"usage\":{\"prompt_tokens\":8,\"completion_tokens\":2,\"total_tokens\":10}}");
                dataConsumer.accept("[DONE]");
                return new StreamTransportResponse(200, Map.of("content-type", "text/event-stream"), "");
            }
        };
        AiGatewayService service = service(mapped, transport, cipher, mapper);

        Response response = service.executeStreaming("account-1", new Request("qwen-plus",
                List.of(new Message("user", "hello")), true, Map.of()), deltas::add);

        assertEquals(true, outgoingBody.get().path("stream").asBoolean());
        assertEquals("gpt-5.6-sol", outgoingBody.get().path("model").asText());
        assertEquals(List.of("你", "好"), deltas);
        assertEquals("你好", response.text());
        assertEquals(8, response.usage().inputTokens());
        assertEquals(2, response.usage().outputTokens());
    }

    @Test
    void sendsTheMappedProviderModelInsteadOfThePlatformModel() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ApiKeyCipher cipher = new ApiKeyCipher(Base64.getEncoder().encodeToString(new byte[32]));
        Channel mapped = new Channel("channel-1", AiProtocol.OPENAI_CHAT,
                URI.create("https://provider.example/v1"), cipher.encrypt("test-key"),
                "gpt-5.6-sol", 0, 100, true);
        JsonNode providerResponse = mapper.readTree("""
                {"id":"response-1","model":"gpt-5.6-sol","choices":[{"message":{"content":"OK"}}],
                 "usage":{"prompt_tokens":4,"completion_tokens":1,"total_tokens":5}}
                """);
        AtomicReference<JsonNode> outgoingBody = new AtomicReference<>();
        AtomicReference<String> requestedPlatformModel = new AtomicReference<>();
        AtomicReference<Duration> requestedTimeout = new AtomicReference<>();

        AiChannelPort channels = new AiChannelPort() {
            @Override
            public List<Channel> findEligible(String accountId, String model) {
                requestedPlatformModel.set(model);
                return List.of(mapped);
            }

            @Override public void recordSuccess(String channelId, long latencyMillis) {}
            @Override public void recordFailure(String channelId, String failureCode) {}
        };
        AiTransportPort transport = (uri, method, headers, body, timeout) -> {
            outgoingBody.set(body);
            requestedTimeout.set(timeout);
            return new AiTransportPort.TransportResponse(200, Map.of(), providerResponse);
        };
        SsrfGuard ssrf = new SsrfGuard(host -> new InetAddress[] {
                InetAddress.getByAddress(new byte[] {93, (byte) 184, (byte) 216, 34})
        });
        AiGatewayService service = new AiGatewayService(channels, transport, cipher, ssrf,
                new WeightedChannelRouter(new Random(1)), mapper);

        Response response = service.execute("account-1", new Request("qwen-plus",
                List.of(new Message("user", "hello")), false, Map.of(
                        "temperature", mapper.getNodeFactory().numberNode(0.1),
                        "_request_timeout_seconds", mapper.getNodeFactory().numberNode(150))));

        assertEquals("qwen-plus", requestedPlatformModel.get());
        assertEquals("gpt-5.6-sol", outgoingBody.get().path("model").asText());
        assertEquals(0.1, outgoingBody.get().path("temperature").asDouble());
        assertEquals(false, outgoingBody.get().has("_request_timeout_seconds"));
        assertEquals(Duration.ofSeconds(150), requestedTimeout.get());
        assertEquals("gpt-5.6-sol", response.model());
    }

    private static AiGatewayService service(Channel channel, AiTransportPort transport, ApiKeyCipher cipher,
            ObjectMapper mapper) throws Exception {
        AiChannelPort channels = new AiChannelPort() {
            @Override public List<Channel> findEligible(String accountId, String model) { return List.of(channel); }
            @Override public void recordSuccess(String channelId, long latencyMillis) {}
            @Override public void recordFailure(String channelId, String failureCode) {}
        };
        SsrfGuard ssrf = new SsrfGuard(host -> new InetAddress[] {
                InetAddress.getByAddress(new byte[] {93, (byte) 184, (byte) 216, 34})
        });
        return new AiGatewayService(channels, transport, cipher, ssrf,
                new WeightedChannelRouter(new Random(1)), mapper);
    }
}
