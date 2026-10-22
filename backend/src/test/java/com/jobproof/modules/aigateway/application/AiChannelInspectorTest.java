package com.jobproof.modules.aigateway.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobproof.modules.aigateway.domain.AiGatewayModels.Channel;
import com.jobproof.modules.aigateway.domain.AiProtocol;
import com.jobproof.modules.aigateway.security.ApiKeyCipher;
import com.jobproof.modules.aigateway.security.SsrfGuard;
import com.jobproof.support.AiProviderStub;
import java.net.InetAddress;
import java.net.URI;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;

class AiChannelInspectorTest {
    final AiProviderStub provider = new AiProviderStub();
    final ApiKeyCipher cipher = new ApiKeyCipher(Base64.getEncoder().encodeToString(new byte[32]));
    final AiChannelInspector inspector = new AiChannelInspector(provider, cipher,
            new SsrfGuard(host -> new InetAddress[] { InetAddress.getByName("93.184.216.34") }), new ObjectMapper());
    final Channel channel = new Channel("channel", AiProtocol.OPENAI_CHAT, URI.create("https://provider.example/v1"),
            cipher.encrypt("test-only-key"), 0, 100, true);

    @Test
    void automaticSelectionSkipsRejectedTimedOutNonStreamingAndMalformedModels() {
        var result = inspector.inspectCapabilities(channel, null);
        assertThat(result.passed()).isTrue();
        assertThat(result.testedModel()).isEqualTo("working-model");
        assertThat(result.models()).containsExactlyElementsOf(provider.models);
        assertThat(provider.calls).contains("embedding-only", "timed-out", "plain-only", "bad-json", "working-model")
                .doesNotContain("unused-model");
    }

    @Test
    void explicitModelNeverFallsBackToAnotherModel() {
        var result = inspector.inspectCapabilities(channel, "  embedding-only  ");
        assertThat(result.passed()).isFalse();
        assertThat(result.testedModel()).isEqualTo("embedding-only");
        assertThat(provider.calls).containsExactly("embedding-only");
    }

    @Test
    void anEmptyStreamDoesNotCountAsWorking() {
        var result = inspector.inspectCapabilities(channel, "plain-only");
        assertThat(result.passed()).isFalse();
        assertThat(result.failureCode()).isEqualTo("STREAM_RESPONSE_FAILED");
    }

    @Test
    void allFailuresPreserveDiscoveryAndDoNotSelectAnUnverifiedModel() {
        provider.models = List.of("embedding-only", "bad-json");
        var result = inspector.inspectCapabilities(channel, "");
        assertThat(result.passed()).isFalse();
        assertThat(result.authenticated()).isTrue();
        assertThat(result.modelDiscovery()).isTrue();
        assertThat(provider.calls).contains("bad-json");
    }

    @Test
    void unknownExplicitModelAndEmptyDiscoveryDoNotInvokeGeneration() {
        assertThat(inspector.inspectCapabilities(channel, "unknown").failureCode()).isEqualTo("MODEL_NOT_DISCOVERED");
        provider.models = List.of();
        assertThat(inspector.inspectCapabilities(channel, null).failureCode()).isEqualTo("MODEL_DISCOVERY_EMPTY");
        assertThat(provider.calls).isEmpty();
    }
}
