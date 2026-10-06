package com.jobproof.modules.aigateway.port;
import com.jobproof.modules.aigateway.domain.AiGatewayModels.Channel; import java.util.List;
public interface AiChannelPort { List<Channel> findEligible(String accountId,String model); void recordSuccess(String channelId,long latencyMillis); void recordFailure(String channelId,String failureCode); }