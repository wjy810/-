package com.jobproof.modules.aiconfig.infra;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
public interface AiModelPricingJpaRepository extends JpaRepository<AiModelPricingEntity,String> {
    Optional<AiModelPricingEntity> findFirstByModelIdAndStatusAndEffectiveFromLessThanEqualAndEffectiveToIsNullOrderByEffectiveFromDesc(String modelId,String status,Instant at);
}