package com.jobproof.modules.aiconfig.infra;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
public interface AiModelJpaRepository extends JpaRepository<AiModelEntity,String> { Optional<AiModelEntity> findByModelCode(String modelCode); }