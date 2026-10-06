package com.jobproof.modules.aiconfig.infra;
import com.jobproof.modules.aiconfig.domain.AiChannelScope;
import com.jobproof.modules.aiconfig.domain.AiChannelStatus;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
public interface AiChannelJpaRepository extends JpaRepository<AiChannelEntity,String> {
    List<AiChannelEntity> findByOwnerAccountIdOrderByCreatedAtDesc(String accountId);
    List<AiChannelEntity> findByScopeAndStatusOrderByCreatedAtDesc(String scope,String status);
    Page<AiChannelEntity> findByScopeAndStatus(AiChannelScope scope, AiChannelStatus status, Pageable pageable);
    Page<AiChannelEntity> findByScopeAndOwnerAccountIdAndStatusNot(AiChannelScope scope, String ownerAccountId,
            AiChannelStatus status, Pageable pageable);
}