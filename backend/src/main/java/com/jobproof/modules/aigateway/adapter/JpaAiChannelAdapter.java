package com.jobproof.modules.aigateway.adapter;

import com.jobproof.modules.aiconfig.domain.AiChannelScope;
import com.jobproof.modules.aiconfig.domain.AiChannelStatus;
import com.jobproof.modules.aiconfig.infra.AiChannelEntity;
import com.jobproof.modules.aiconfig.infra.AiChannelHealthEntity;
import com.jobproof.modules.aiconfig.infra.AiChannelHealthJpaRepository;
import com.jobproof.modules.aiconfig.infra.AiChannelJpaRepository;
import com.jobproof.modules.aiconfig.infra.AiChannelModelJpaRepository;
import com.jobproof.modules.aiconfig.infra.AiModelJpaRepository;
import com.jobproof.modules.aigateway.domain.AiGatewayModels.Channel;
import com.jobproof.modules.aigateway.domain.AiProtocol;
import com.jobproof.modules.aigateway.port.AiChannelPort;
import com.jobproof.shared.id.Ids;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class JpaAiChannelAdapter implements AiChannelPort {
    private final AiChannelJpaRepository channels;private final AiChannelModelJpaRepository mappings;
    private final AiModelJpaRepository models;private final AiChannelHealthJpaRepository health;
    public JpaAiChannelAdapter(AiChannelJpaRepository channels,AiChannelModelJpaRepository mappings,AiModelJpaRepository models,AiChannelHealthJpaRepository health){this.channels=channels;this.mappings=mappings;this.models=models;this.health=health;}
    @Override @Transactional(readOnly=true)
    public List<Channel> findEligible(String accountId,String model){
        var requested=models.findByModelCode(model).orElse(null);if(requested==null||!"ACTIVE".equals(requested.getStatus()))return List.of();
        List<Channel> result=new ArrayList<>();
        for(AiChannelEntity entity:channels.findAll()){
            if(entity.getStatus()!=AiChannelStatus.ACTIVE)continue;
            if(entity.getScope()==AiChannelScope.PERSONAL&&!entity.getOwnerAccountId().equals(accountId))continue;
            var explicit=mappings.findByChannelIdAndModelId(entity.getId(),requested.getId()).orElse(null);
            if(explicit!=null&&(!"ACTIVE".equals(explicit.getStatus())||explicit.isHidden()))continue;
            if(explicit==null&&!entity.getProviderCode().equalsIgnoreCase(requested.getProviderCode()))continue;
            AiChannelHealthEntity snapshot=health.findByChannelId(entity.getId()).orElse(null);
            int priority=snapshot==null||"UNKNOWN".equals(snapshot.getStatus())?1:"HEALTHY".equals(snapshot.getStatus())?0:"DEGRADED".equals(snapshot.getStatus())?2:3;
            String providerModel=explicit==null?model:explicit.getProviderModelCode();
            result.add(new Channel(entity.getId(),AiProtocol.valueOf(entity.getProtocol().trim().toUpperCase()),URI.create(entity.getBaseUrl()),entity.getApiKeyCiphertext(),providerModel,priority,100,true));
        }
        return List.copyOf(result);
    }
    @Override @Transactional public void recordSuccess(String channelId,long latencyMillis){AiChannelHealthEntity value=value(channelId);value.setStatus("HEALTHY");value.setLatencyMs(latencyMillis);value.setConsecutiveFailures(0);value.setFailureCode(null);value.setCheckedAt(Instant.now());health.save(value);}
    @Override @Transactional public void recordFailure(String channelId,String failureCode){AiChannelHealthEntity value=value(channelId);value.setStatus(value.getConsecutiveFailures()>=2?"UNHEALTHY":"DEGRADED");value.setConsecutiveFailures(value.getConsecutiveFailures()+1);value.setFailureCode(failureCode);value.setCheckedAt(Instant.now());health.save(value);}
    private AiChannelHealthEntity value(String channelId){return health.findByChannelId(channelId).orElseGet(()->{AiChannelHealthEntity value=new AiChannelHealthEntity();value.setId(Ids.newId());value.setChannelId(channelId);value.setStatus("UNKNOWN");value.setConsecutiveFailures(0);value.setCheckedAt(Instant.now());return value;});}
}
