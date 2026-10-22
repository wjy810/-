package com.jobproof.modules.aigateway.port;

import com.jobproof.modules.aigateway.domain.AiGatewayModels.Usage;

public interface AiCallObserver {
    void record(String channelId, String model, Usage usage, String failureCode);
    AiCallObserver NONE = (channel, model, usage, failure) -> {};
}
