package com.jobproof.modules.resume.application;

import com.jobproof.modules.resume.domain.ResumeLayoutProtocol;
import com.jobproof.modules.resume.infra.ResumeLayoutTemplateEntity;
import com.jobproof.modules.resume.infra.ResumeLayoutTemplateJpaRepository;
import com.jobproof.modules.resume.infra.ResumeLayoutTemplateVersionEntity;
import com.jobproof.modules.resume.infra.ResumeLayoutTemplateVersionJpaRepository;
import com.jobproof.modules.resume.infra.ResumeTemplateCatalogEntryEntity;
import com.jobproof.modules.resume.infra.ResumeTemplateCatalogEntryJpaRepository;
import com.jobproof.modules.resume.infra.ResumeTemplateCatalogFacetEntity;
import com.jobproof.modules.resume.infra.ResumeTemplateCatalogFacetJpaRepository;
import com.jobproof.shared.id.Ids;
import com.jobproof.shared.time.ClockPort;
import java.time.Instant;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class ResumeSmartTemplateCatalogPublisher {
    private static final Map<String, Occupation> OCCUPATIONS = Map.of(
            "engineer", new Occupation("TECHNOLOGY", "技术研发"),
            "banker", new Occupation("FINANCE", "财务金融"),
            "academic", new Occupation("EDUCATION_RESEARCH", "教育科研"),
            "ledger", new Occupation("PRODUCT_OPERATIONS", "产品运营"),
            "editorial", new Occupation("DESIGN_CREATIVE", "设计创意"),
            "bold", new Occupation("MARKETING_MEDIA", "市场传媒"));

    private final ResumeLayoutTemplateJpaRepository templates;
    private final ResumeLayoutTemplateVersionJpaRepository versions;
    private final ResumeTemplateCatalogEntryJpaRepository entries;
    private final ResumeTemplateCatalogFacetJpaRepository facets;
    private final ClockPort clock;
    private final ResumeTemplateCatalogService catalogService;
    private final BuiltInTemplateCatalog builtIns;

    public ResumeSmartTemplateCatalogPublisher(ResumeLayoutTemplateJpaRepository templates,
            ResumeLayoutTemplateVersionJpaRepository versions,
            ResumeTemplateCatalogEntryJpaRepository entries,
            ResumeTemplateCatalogFacetJpaRepository facets, ClockPort clock,
            ResumeTemplateCatalogService catalogService, BuiltInTemplateCatalog builtIns) {
        this.templates = templates;
        this.versions = versions;
        this.entries = entries;
        this.facets = facets;
        this.clock = clock;
        this.catalogService = catalogService;
        this.builtIns = builtIns;
    }

    public void sync(String templateId) {
        ResumeLayoutTemplateEntity template = templates.findById(templateId).orElse(null);
        ResumeLayoutTemplateVersionEntity version = versions
                .findFirstByTemplateIdAndStatusOrderByRevisionNoDesc(templateId, "PUBLISHED")
                .filter(ResumeSmartTemplateCatalogPublisher::allGatesPassed)
                .orElse(null);
        if (template == null || !"PUBLISHED".equals(template.getStatus()) || version == null) {
            hideExisting(templateId);
            return;
        }

        Instant now = clock.now();
        ResumeTemplateCatalogEntryEntity entry = entries
                .findByEntryTypeAndReferenceId("SMART_TEMPLATE", templateId)
                .orElseGet(ResumeTemplateCatalogEntryEntity::new);
        if (entry.getId() == null) {
            entry.setId(Ids.newId());
            entry.setCreatedAt(now);
            entry.setDownloadCount(0);
            entry.setVersionNo(0);
        }
        entry.setEntryType("SMART_TEMPLATE");
        entry.setReferenceId(templateId);
        entry.setTitle(template.getDisplayName());
        entry.setSummary(builtIns.find(templateId).map(value -> value.manifest().summary())
                .orElse("支持在线编辑、字段级 AI 候选和受控 PDF/DOCX 导出。"));
        entry.setCapability("SMART_EDITABLE");
        entry.setAssetKind("RESUME");
        entry.setLanguageCode(template.getLanguageCode());
        entry.setPageCount(template.getRecommendedPages());
        entry.setPhotoPolicy(template.getPhotoPolicy());
        entry.setThumbnailUri(version.getThumbnailUri());
        entry.setPreviewStatus("NOT_APPLICABLE");
        entry.setPreviewPageCount(0);
        entry.setPreviewError(null);
        entry.setPreviewRendererVersion(null);
        entry.setPreviewUpdatedAt(now);
        entry.setSourceName("JobProof AI");
        entry.setSourceUri("/resume-templates/" + templateId);
        entry.setAttribution(template.isBuiltin() ? "JobProof 内置模板" : "JobProof AI 独立受控版式");
        entry.setSearchText(template.getDisplayName() + " " + template.getFamilyName() + " " + templateId);
        entry.setPublicationStatus("PUBLISHED");
        entry.setPublishedAt(entry.getPublishedAt() == null ? now : entry.getPublishedAt());
        entry.setRetiredAt(null);
        entry.setUpdatedAt(now);
        entries.save(entry);

        facets.deleteByCatalogEntryId(entry.getId());
        facets.flush();
        Occupation occupation = OCCUPATIONS.getOrDefault(templateId,
                new Occupation("GENERAL", "通用其他"));
        ResumeTemplateCatalogFacetEntity facet = new ResumeTemplateCatalogFacetEntity();
        facet.setId(Ids.newId());
        facet.setCatalogEntryId(entry.getId());
        facet.setFacetType("OCCUPATION");
        facet.setFacetCode(occupation.code());
        facet.setFacetLabel(occupation.label());
        facets.save(facet);
        catalogService.invalidatePublicSnapshot();
    }

    private void hideExisting(String templateId) {
        entries.findByEntryTypeAndReferenceId("SMART_TEMPLATE", templateId).ifPresent(entry -> {
            Instant now = clock.now();
            entry.setPublicationStatus(entry.getPublishedAt() == null ? "HIDDEN" : "RETIRED");
            entry.setRetiredAt(entry.getPublishedAt() == null ? null : now);
            entry.setUpdatedAt(now);
            entries.save(entry);
            catalogService.invalidatePublicSnapshot();
        });
    }

    /**
     * Built-in HTML templates export template-faithful PDF only; their Word file is the generic ATS
     * layout, so the Word / WPS gates do not apply to them.
     */
    private static boolean allGatesPassed(ResumeLayoutTemplateVersionEntity value) {
        boolean core = value.isAuthorizationVerified() && value.isSecurityVerified() && value.isRenderVerified()
                && value.isAtsVerified();
        return ResumeLayoutProtocol.isRenderV4(value.getRendererProtocol())
                ? core : core && value.isWordVerified() && value.isWpsVerified();
    }

    private record Occupation(String code, String label) {}
}
