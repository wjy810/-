package com.jobproof.modules.resume.infra;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ResumeTemplateCatalogFacetJpaRepository extends JpaRepository<ResumeTemplateCatalogFacetEntity,String> {
    List<ResumeTemplateCatalogFacetEntity> findByCatalogEntryId(String catalogEntryId);
    List<ResumeTemplateCatalogFacetEntity> findByCatalogEntryIdIn(Collection<String> catalogEntryIds);
    void deleteByCatalogEntryId(String catalogEntryId);
}
