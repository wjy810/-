package com.jobproof.infrastructure.config;
import com.fasterxml.jackson.databind.ObjectMapper;import com.jobproof.modules.aigateway.application.*;import com.jobproof.modules.aigateway.port.*;import com.jobproof.modules.aigateway.security.*;import java.util.Random;import org.springframework.context.annotation.*;
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
    public AiGatewayService aiGatewayService(AiChannelPort channels,AiTransportPort transport,ApiKeyCipher cipher,
            SsrfGuard ssrf,WeightedChannelRouter router,ObjectMapper mapper){return new AiGatewayService(channels,transport,cipher,ssrf,router,mapper);}

    @Bean
    public AiChannelInspector aiChannelInspector(AiTransportPort transport,ApiKeyCipher cipher,SsrfGuard ssrf,ObjectMapper mapper){return new AiChannelInspector(transport,cipher,ssrf,mapper);}
}
