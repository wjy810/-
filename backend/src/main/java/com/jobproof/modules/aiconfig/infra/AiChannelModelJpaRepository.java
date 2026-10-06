package com.jobproof.modules.aiconfig.infra;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
public interface AiChannelModelJpaRepository extends JpaRepository<AiChannelModelEntity,String> {
    List<AiChannelModelEntity> findByChannelIdOrderByPriorityAsc(String channelId);
    Optional<AiChannelModelEntity> findByChannelIdAndModelId(String channelId,String modelId);
}