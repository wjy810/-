package com.jobproof.modules.resume.infra;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "resume_layout_template_versions")
public class ResumeLayoutTemplateVersionEntity {
    @Id private String id;
    @Column(name = "template_id") private String templateId;
    @Column(name = "revision_no") private int revisionNo;
    private String status;
    @Column(name = "renderer_protocol") private String rendererProtocol;
    @Column(name = "definition_json", columnDefinition = "LONGTEXT") private String definitionJson;
    @Column(name = "thumbnail_uri") private String thumbnailUri;
    @Column(name = "source_asset_id") private String sourceAssetId;
    @Column(name = "independent_design_evidence_id") private String independentDesignEvidenceId;
    @Column(name = "authorization_verified") private boolean authorizationVerified;
    @Column(name = "security_verified") private boolean securityVerified;
    @Column(name = "render_verified") private boolean renderVerified;
    @Column(name = "word_verified") private boolean wordVerified;
    @Column(name = "wps_verified") private boolean wpsVerified;
    @Column(name = "ats_verified") private boolean atsVerified;
    @Column(name = "test_report_json", columnDefinition = "LONGTEXT") private String testReportJson;
    @Column(name = "version_no") private int versionNo;
    @Column(name = "published_at") private Instant publishedAt;
    @Column(name = "retired_at") private Instant retiredAt;
    @Column(name = "created_at") private Instant createdAt;
    @Column(name = "updated_at") private Instant updatedAt;

    public String getId() { return id; } public void setId(String id) { this.id = id; }
    public String getTemplateId() { return templateId; } public void setTemplateId(String value) { templateId = value; }
    public int getRevisionNo() { return revisionNo; } public void setRevisionNo(int value) { revisionNo = value; }
    public String getStatus() { return status; } public void setStatus(String value) { status = value; }
    public String getRendererProtocol() { return rendererProtocol; } public void setRendererProtocol(String value) { rendererProtocol = value; }
    public String getDefinitionJson() { return definitionJson; } public void setDefinitionJson(String value) { definitionJson = value; }
    public String getThumbnailUri() { return thumbnailUri; } public void setThumbnailUri(String value) { thumbnailUri = value; }
    public String getSourceAssetId() { return sourceAssetId; } public void setSourceAssetId(String value) { sourceAssetId = value; }
    public String getIndependentDesignEvidenceId() { return independentDesignEvidenceId; } public void setIndependentDesignEvidenceId(String value) { independentDesignEvidenceId = value; }
    public boolean isAuthorizationVerified() { return authorizationVerified; } public void setAuthorizationVerified(boolean value) { authorizationVerified = value; }
    public boolean isSecurityVerified() { return securityVerified; } public void setSecurityVerified(boolean value) { securityVerified = value; }
    public boolean isRenderVerified() { return renderVerified; } public void setRenderVerified(boolean value) { renderVerified = value; }
    public boolean isWordVerified() { return wordVerified; } public void setWordVerified(boolean value) { wordVerified = value; }
    public boolean isWpsVerified() { return wpsVerified; } public void setWpsVerified(boolean value) { wpsVerified = value; }
    public boolean isAtsVerified() { return atsVerified; } public void setAtsVerified(boolean value) { atsVerified = value; }
    public String getTestReportJson() { return testReportJson; } public void setTestReportJson(String value) { testReportJson = value; }
    public int getVersionNo() { return versionNo; } public void setVersionNo(int value) { versionNo = value; }
    public Instant getPublishedAt() { return publishedAt; } public void setPublishedAt(Instant value) { publishedAt = value; }
    public Instant getRetiredAt() { return retiredAt; } public void setRetiredAt(Instant value) { retiredAt = value; }
    public Instant getCreatedAt() { return createdAt; } public void setCreatedAt(Instant value) { createdAt = value; }
    public Instant getUpdatedAt() { return updatedAt; } public void setUpdatedAt(Instant value) { updatedAt = value; }
}
