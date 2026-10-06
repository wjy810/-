package com.jobproof.modules.resume.infra;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ResumeTemplateImportItemJpaRepository extends JpaRepository<ResumeTemplateImportItemEntity,String> {
    Optional<ResumeTemplateImportItemEntity> findByBatchIdAndSourceRelativePath(String batchId,String path);
    Optional<ResumeTemplateImportItemEntity> findFirstByBatchIdAndStatusOrderBySourceRelativePathAsc(String batchId,String status);
    List<ResumeTemplateImportItemEntity> findByBatchId(String batchId);
    Page<ResumeTemplateImportItemEntity> findByBatchIdOrderBySourceRelativePathAsc(String batchId,Pageable pageable);
}

