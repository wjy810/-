package com.jobproof.modules.resume.infra;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ResumeTemplateCatalogEntryJpaRepository extends JpaRepository<ResumeTemplateCatalogEntryEntity,String> {
    Optional<ResumeTemplateCatalogEntryEntity> findByEntryTypeAndReferenceId(String entryType,String referenceId);
    List<ResumeTemplateCatalogEntryEntity> findByPublicationStatus(String publicationStatus);
    Page<ResumeTemplateCatalogEntryEntity> findAllByOrderByUpdatedAtDesc(Pageable pageable);
    List<ResumeTemplateCatalogEntryEntity> findByEntryTypeAndPreviewStatusOrderByPreviewUpdatedAtAsc(
            String entryType, String previewStatus, Pageable pageable);
    List<ResumeTemplateCatalogEntryEntity> findByEntryTypeAndPreviewStatusAndPreviewUpdatedAtBefore(
            String entryType, String previewStatus, java.time.Instant before);
    long countByEntryTypeAndPreviewStatus(String entryType, String previewStatus);
}
