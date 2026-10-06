package com.jobproof.modules.aigateway.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.junit.jupiter.api.Test;

class AiGatewayConfigurationTest {

    @Test
    void createsRouterWithoutOptionalRandomGeneratorModules() {
        AiGatewayConfiguration configuration = new AiGatewayConfiguration();
        assertDoesNotThrow(configuration::weightedChannelRouter);
    }
}
