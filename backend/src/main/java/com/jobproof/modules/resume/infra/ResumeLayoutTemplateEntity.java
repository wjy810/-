package com.jobproof.modules.resume.infra;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "resume_layout_templates")
public class ResumeLayoutTemplateEntity {
    @Id private String id;
    @Column(name = "display_name") private String displayName;
    @Column(name = "family_name") private String familyName;
    @Column(name = "language_code") private String languageCode;
    @Column(name = "recommended_pages") private String recommendedPages;
    @Column(name = "ats_candidate_level") private String atsCandidateLevel;
    @Column(name = "photo_policy") private String photoPolicy;
    @Column(name = "variants_json", columnDefinition = "TEXT") private String variantsJson;
    @Column(name = "tags_json", columnDefinition = "TEXT") private String tagsJson;
    private String status;
    private boolean builtin;
    @Column(name = "sort_order") private int sortOrder = 1000;
    private boolean featured;
    @Column(name = "created_at") private Instant createdAt;
    @Column(name = "updated_at") private Instant updatedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getFamilyName() { return familyName; }
    public void setFamilyName(String familyName) { this.familyName = familyName; }
    public String getLanguageCode() { return languageCode; }
    public void setLanguageCode(String languageCode) { this.languageCode = languageCode; }
    public String getRecommendedPages() { return recommendedPages; }
    public void setRecommendedPages(String recommendedPages) { this.recommendedPages = recommendedPages; }
    public String getAtsCandidateLevel() { return atsCandidateLevel; }
    public void setAtsCandidateLevel(String atsCandidateLevel) { this.atsCandidateLevel = atsCandidateLevel; }
    public String getPhotoPolicy() { return photoPolicy; }
    public void setPhotoPolicy(String photoPolicy) { this.photoPolicy = photoPolicy; }
    public String getVariantsJson() { return variantsJson; }
    public void setVariantsJson(String variantsJson) { this.variantsJson = variantsJson; }
    public String getTagsJson() { return tagsJson; }
    public void setTagsJson(String tagsJson) { this.tagsJson = tagsJson; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public boolean isBuiltin() { return builtin; }
    public void setBuiltin(boolean value) { builtin = value; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int value) { sortOrder = value; }
    public boolean isFeatured() { return featured; }
    public void setFeatured(boolean value) { featured = value; }
}
