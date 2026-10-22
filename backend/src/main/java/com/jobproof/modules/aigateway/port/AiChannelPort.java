package com.jobproof.modules.aigateway.port;
import com.jobproof.modules.aigateway.domain.AiGatewayModels.Channel; import java.util.List;
public interface AiChannelPort {
    List<Channel> findEligible(String accountId, String model);
    /** Read-only availability; execution must use the validated reservation context. */
    default List<Channel> findAvailable(String accountId, String model) { return findEligible(accountId, model); }
    void recordSuccess(String channelId, long latencyMillis);
    void recordFailure(String channelId, String failureCode);
}
