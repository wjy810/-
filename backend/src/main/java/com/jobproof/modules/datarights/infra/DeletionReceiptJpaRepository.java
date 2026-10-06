package com.jobproof.modules.datarights.infra;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeletionReceiptJpaRepository extends JpaRepository<DeletionReceiptEntity, String> {
    List<DeletionReceiptEntity> findByRequestId(String requestId);
}
