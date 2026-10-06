package com.jobproof.modules.resume.infra;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.Collection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
public interface ResumeTemplateImportBatchJpaRepository extends JpaRepository<ResumeTemplateImportBatchEntity,String> {
    Optional<ResumeTemplateImportBatchEntity> findBySourceCode(String sourceCode);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ResumeTemplateImportBatchEntity> findFirstByStatusInOrderByCreatedAtAsc(Collection<String> statuses);
    Page<ResumeTemplateImportBatchEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
