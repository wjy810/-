package com.jobproof.modules.aigateway.application;
import com.fasterxml.jackson.databind.JsonNode; import com.fasterxml.jackson.databind.ObjectMapper; import com.jobproof.modules.aigateway.adapter.*; import com.jobproof.modules.aigateway.domain.AiGatewayModels.*; import com.jobproof.modules.aigateway.port.*; import com.jobproof.modules.aigateway.security.*; import java.time.Duration; import java.util.LinkedHashMap; import java.util.List; import java.util.Map; import java.util.concurrent.atomic.AtomicBoolean; import java.util.concurrent.atomic.AtomicLong; import java.util.concurrent.atomic.AtomicReference; import java.util.function.Consumer;
public final class AiGatewayService {
    private static final String REQUEST_TIMEOUT_OPTION = "_request_timeout_seconds";
    private static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(60);
    private final AiChannelPort channels;
    private final AiTransportPort transport;
    private final ApiKeyCipher cipher;
    private final SsrfGuard ssrf;
    private final WeightedChannelRouter router;
    private final ObjectMapper json;

    public AiGatewayService(AiChannelPort channels, AiTransportPort transport, ApiKeyCipher cipher,
                            SsrfGuard ssrf, WeightedChannelRouter router, ObjectMapper json) {
        this.channels = channels;
        this.transport = transport;
        this.cipher = cipher;
        this.ssrf = ssrf;
        this.router = router;
        this.json = json;
    }

    public Response execute(String accountId, Request request) {
        List<Channel> ordered = router.order(channels.findEligible(accountId, request.model()));
        if (ordered.isEmpty()) {
            throw new AiGatewayException("No eligible AI channel", false, false);
        }
        AiGatewayException last = null;
        for (int attempt = 0; attempt < Math.min(2, ordered.size()); attempt++) {
            Channel channel = ordered.get(attempt);
            long start = System.nanoTime();
            try {
                ssrf.validate(channel.baseUri());
                AiProtocolAdapter adapter = adapter(channel);
                Duration requestTimeout = requestTimeout(request.options());
                Request providerRequest = new Request(channel.resolvedModel(request.model()), request.messages(),
                        request.stream(), providerOptions(request.options()));
                var outgoing = adapter.adapt(channel.baseUri(), cipher.decrypt(channel.encryptedApiKey()), providerRequest);
                ssrf.validate(outgoing.uri());
                var response = transport.exchange(outgoing.uri(), "POST", outgoing.headers(), outgoing.body(), requestTimeout);
                if (response.status() < 200 || response.status() >= 300) {
                    boolean retryable = response.status() == 408 || response.status() == 429 || response.status() >= 500;
                    throw new AiGatewayException("Upstream returned HTTP " + response.status(), retryable, false);
                }
                channels.recordSuccess(channel.id(), (System.nanoTime() - start) / 1_000_000);
                Response parsed=adapter.parse(response.body());
                return new Response(parsed.id(),channel.id(),parsed.model(),parsed.text(),parsed.usage(),parsed.raw());
            } catch (AiGatewayException e) {
                channels.recordFailure(channel.id(), "UPSTREAM_FAILURE");
                last = e;
                if (!e.retryable() || e.outputStarted() || request.stream()) {
                    throw e;
                }
            } catch (RuntimeException e) {
                channels.recordFailure(channel.id(), "TRANSPORT_FAILURE");
                last = new AiGatewayException("AI channel request failed", e, true, false);
                if (request.stream()) {
                    throw last;
                }
            }
        }
        throw last == null ? new AiGatewayException("AI channel request failed", false, false) : last;
    }

    public Response executeStreaming(String accountId, Request request, Consumer<String> deltaConsumer) {
        List<Channel> ordered = router.order(channels.findEligible(accountId, request.model()));
        if (ordered.isEmpty()) throw new AiGatewayException("No eligible AI channel", false, false);
        AiGatewayException last = null;
        for (int attempt = 0; attempt < Math.min(2, ordered.size()); attempt++) {
            Channel channel = ordered.get(attempt);
            AtomicBoolean outputStarted = new AtomicBoolean();
            AtomicBoolean done = new AtomicBoolean();
            AtomicReference<String> responseId = new AtomicReference<>();
            AtomicReference<String> responseModel = new AtomicReference<>();
            AtomicReference<JsonNode> raw = new AtomicReference<>();
            AtomicLong inputTokens = new AtomicLong();
            AtomicLong outputTokens = new AtomicLong();
            AtomicLong totalTokens = new AtomicLong();
            StringBuilder text = new StringBuilder();
            long start = System.nanoTime();
            try {
                ssrf.validate(channel.baseUri());
                AiProtocolAdapter adapter = adapter(channel);
                Duration requestTimeout = requestTimeout(request.options());
                Request providerRequest = new Request(channel.resolvedModel(request.model()), request.messages(),
                        true, providerOptions(request.options()));
                var outgoing = adapter.adapt(channel.baseUri(), cipher.decrypt(channel.encryptedApiKey()), providerRequest);
                ssrf.validate(outgoing.uri());
                Map<String, String> headers = new LinkedHashMap<>(outgoing.headers());
                headers.put("Accept", "text/event-stream");
                var response = transport.exchangeStream(outgoing.uri(), "POST", Map.copyOf(headers), outgoing.body(),
                        requestTimeout, data -> {
                            if (done.get()) return;
                            AiProtocolAdapter.StreamEvent event = adapter.parseStreamData(data);
                            if (event.done()) {
                                done.set(true);
                                return;
                            }
                            if (event.id() != null && !event.id().isBlank()) responseId.set(event.id());
                            if (event.model() != null && !event.model().isBlank()) responseModel.set(event.model());
                            if (event.raw() != null) raw.set(event.raw());
                            if (event.usage() != null) {
                                inputTokens.set(event.usage().inputTokens());
                                outputTokens.set(event.usage().outputTokens());
                                totalTokens.set(event.usage().totalTokens());
                            }
                            if (event.delta() != null && !event.delta().isEmpty()) {
                                outputStarted.set(true);
                                text.append(event.delta());
                                deltaConsumer.accept(event.delta());
                            }
                        });
                if (response.status() < 200 || response.status() >= 300) {
                    boolean retryable = response.status() == 408 || response.status() == 429 || response.status() >= 500;
                    throw new AiGatewayException("Upstream returned HTTP " + response.status(), retryable,
                            outputStarted.get());
                }
                if (!done.get()) throw new AiGatewayException("AI stream ended before [DONE]", true, outputStarted.get());
                channels.recordSuccess(channel.id(), (System.nanoTime() - start) / 1_000_000);
                Usage usage = new Usage(inputTokens.get(), outputTokens.get(), totalTokens.get());
                return new Response(responseId.get(), channel.id(), responseModel.get(), text.toString(), usage, raw.get());
            } catch (AiGatewayException exception) {
                channels.recordFailure(channel.id(), "UPSTREAM_STREAM_FAILURE");
                last = exception.outputStarted() || !outputStarted.get() ? exception
                        : new AiGatewayException(exception.getMessage(), exception, exception.retryable(), true);
                if (!last.retryable() || last.outputStarted()) throw last;
            } catch (RuntimeException exception) {
                channels.recordFailure(channel.id(), "STREAM_TRANSPORT_FAILURE");
                last = new AiGatewayException("AI streaming request failed", exception, true, outputStarted.get());
                if (last.outputStarted()) throw last;
            }
        }
        throw last == null ? new AiGatewayException("AI streaming request failed", false, false) : last;
    }

    private AiProtocolAdapter adapter(Channel channel) {
        return switch (channel.protocol()) {
            case OPENAI_CHAT -> new ProtocolAdapters.OpenAiChat(json);
            case OPENAI_RESPONSES -> new ProtocolAdapters.OpenAiResponses(json);
            case ANTHROPIC_MESSAGES -> new ProtocolAdapters.AnthropicMessages(json);
        };
    }

    private static Duration requestTimeout(Map<String, JsonNode> options) {
        JsonNode configured = options.get(REQUEST_TIMEOUT_OPTION);
        if (configured == null || !configured.canConvertToInt()) return DEFAULT_REQUEST_TIMEOUT;
        return Duration.ofSeconds(Math.max(10, Math.min(180, configured.asInt())));
    }

    private static Map<String, JsonNode> providerOptions(Map<String, JsonNode> options) {
        if (!options.containsKey(REQUEST_TIMEOUT_OPTION)) return options;
        Map<String, JsonNode> sanitized = new LinkedHashMap<>(options);
        sanitized.remove(REQUEST_TIMEOUT_OPTION);
        return Map.copyOf(sanitized);
    }
}
