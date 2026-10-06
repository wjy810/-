package com.jobproof.modules.resume.infra;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "resume_candidates")
public class ResumeCandidateEntity {

    @Id
    private String id;

    @Column(name = "account_id", nullable = false)
    private String accountId;

    @Column(name = "master_id", nullable = false)
    private String masterId;

    @Column(name = "field_key", nullable = false, length = 64)
    private String fieldKey;

    @Column(name = "proposed_value_json", nullable = false, columnDefinition = "TEXT")
    private String proposedValueJson;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "correction_value_json", columnDefinition = "TEXT")
    private String correctionValueJson;

    @Column(name = "candidate_source", nullable = false, length = 32)
    private String candidateSource;

    @Column(name = "ai_action", length = 32)
    private String aiAction;

    @Column(name = "reason_text", length = 2048)
    private String reasonText;

    @Column(name = "diff_json", columnDefinition = "TEXT")
    private String diffJson;

    @Column(name = "source_facts_json", columnDefinition = "TEXT")
    private String sourceFactsJson;

    @Column(name = "generation_metadata_json", columnDefinition = "TEXT")
    private String generationMetadataJson;

    @Column(name = "career_library_snapshot_version")
    private Integer careerLibrarySnapshotVersion;

    @Column(name = "career_library_sources_json", columnDefinition = "TEXT")
    private String careerLibrarySourcesJson;

    @Column(name = "source_stale", nullable = false)
    private boolean sourceStale;

    @Column(name = "version_no", nullable = false)
    private int versionNo;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getAccountId() {
        return accountId;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }

    public String getMasterId() {
        return masterId;
    }

    public void setMasterId(String masterId) {
        this.masterId = masterId;
    }

    public String getFieldKey() {
        return fieldKey;
    }

    public void setFieldKey(String fieldKey) {
        this.fieldKey = fieldKey;
    }

    public String getProposedValueJson() {
        return proposedValueJson;
    }

    public void setProposedValueJson(String proposedValueJson) {
        this.proposedValueJson = proposedValueJson;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getCorrectionValueJson() {
        return correctionValueJson;
    }

    public void setCorrectionValueJson(String correctionValueJson) {
        this.correctionValueJson = correctionValueJson;
    }
    public String getCandidateSource(){return candidateSource;} public void setCandidateSource(String value){candidateSource=value;}
    public String getAiAction(){return aiAction;} public void setAiAction(String value){aiAction=value;}
    public String getReasonText(){return reasonText;} public void setReasonText(String value){reasonText=value;}
    public String getDiffJson(){return diffJson;} public void setDiffJson(String value){diffJson=value;}
    public String getSourceFactsJson(){return sourceFactsJson;} public void setSourceFactsJson(String value){sourceFactsJson=value;}
    public String getGenerationMetadataJson(){return generationMetadataJson;} public void setGenerationMetadataJson(String value){generationMetadataJson=value;}
    public Integer getCareerLibrarySnapshotVersion(){return careerLibrarySnapshotVersion;} public void setCareerLibrarySnapshotVersion(Integer value){careerLibrarySnapshotVersion=value;}
    public String getCareerLibrarySourcesJson(){return careerLibrarySourcesJson;} public void setCareerLibrarySourcesJson(String value){careerLibrarySourcesJson=value;}
    public boolean isSourceStale(){return sourceStale;} public void setSourceStale(boolean value){sourceStale=value;}

    public int getVersionNo() {
        return versionNo;
    }

    public void setVersionNo(int versionNo) {
        this.versionNo = versionNo;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public void setDecidedAt(Instant decidedAt) {
        this.decidedAt = decidedAt;
    }
}
