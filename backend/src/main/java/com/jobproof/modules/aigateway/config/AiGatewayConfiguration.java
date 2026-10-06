package com.jobproof.modules.aigateway.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobproof.modules.aigateway.application.AiChannelInspector;
import com.jobproof.modules.aigateway.application.AiGatewayService;
import com.jobproof.modules.aigateway.application.WeightedChannelRouter;
import com.jobproof.modules.aigateway.port.AiChannelPort;
import com.jobproof.modules.aigateway.port.AiTransportPort;
import com.jobproof.modules.aigateway.security.ApiKeyCipher;
import com.jobproof.modules.aigateway.security.MasterKeyProperties;
import com.jobproof.modules.aigateway.security.SsrfGuard;
import java.util.Random;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires the AI gateway; lives in its module so infrastructure stays module-agnostic (rule R3). */
@Configuration
public class AiGatewayConfiguration {

    @Bean
    public SsrfGuard aiSsrfGuard() {
        return new SsrfGuard();
    }

    @Bean
    public WeightedChannelRouter weightedChannelRouter() {
        return new WeightedChannelRouter(new Random());
    }

    @Bean
    public ApiKeyCipher apiKeyCipher(MasterKeyProperties properties) {
        return new ApiKeyCipher(properties.getMasterKey());
    }

    @Bean
    public AiGatewayService aiGatewayService(AiChannelPort channels, AiTransportPort transport, ApiKeyCipher cipher,
            SsrfGuard ssrf, WeightedChannelRouter router, ObjectMapper mapper) {
        return new AiGatewayService(channels, transport, cipher, ssrf, router, mapper);
    }

    @Bean
    public AiChannelInspector aiChannelInspector(AiTransportPort transport, ApiKeyCipher cipher, SsrfGuard ssrf, ObjectMapper mapper) {
        return new AiChannelInspector(transport, cipher, ssrf, mapper);
    }
}
