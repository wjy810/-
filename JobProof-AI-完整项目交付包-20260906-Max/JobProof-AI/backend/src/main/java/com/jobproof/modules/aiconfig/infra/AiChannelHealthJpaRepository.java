package com.jobproof.modules.aiconfig.infra;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
public interface AiChannelHealthJpaRepository extends JpaRepository<AiChannelHealthEntity,String> { Optional<AiChannelHealthEntity> findByChannelId(String channelId); }