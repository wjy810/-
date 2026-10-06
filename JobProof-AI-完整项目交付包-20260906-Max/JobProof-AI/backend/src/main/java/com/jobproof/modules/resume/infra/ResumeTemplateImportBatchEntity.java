package com.jobproof.modules.resume.infra;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "resume_template_import_batches")
public class ResumeTemplateImportBatchEntity {
    @Id private String id;
    @Column(name="source_code") private String sourceCode;
    @Column(name="source_name") private String sourceName;
    @Column(name="source_uri") private String sourceUri;
    @Column(name="license_evidence_id") private String licenseEvidenceId;
    @Column(name="distribution_mode") private String distributionMode;
    private String status;
    @Column(name="total_count") private int totalCount;
    @Column(name="processed_count") private int processedCount;
    @Column(name="ready_count") private int readyCount;
    @Column(name="duplicate_count") private int duplicateCount;
    @Column(name="rejected_count") private int rejectedCount;
    @Column(name="quarantined_count") private int quarantinedCount;
    @Column(name="unsupported_count") private int unsupportedCount;
    @Column(name="error_message") private String errorMessage;
    @Column(name="requested_by") private String requestedBy;
    @Column(name="started_at") private Instant startedAt;
    @Column(name="completed_at") private Instant completedAt;
    @Column(name="created_at") private Instant createdAt;
    @Column(name="updated_at") private Instant updatedAt;
    @Column(name="version_no") private int versionNo;
    public String getId(){return id;} public void setId(String v){id=v;}
    public String getSourceCode(){return sourceCode;} public void setSourceCode(String v){sourceCode=v;}
    public String getSourceName(){return sourceName;} public void setSourceName(String v){sourceName=v;}
    public String getSourceUri(){return sourceUri;} public void setSourceUri(String v){sourceUri=v;}
    public String getLicenseEvidenceId(){return licenseEvidenceId;} public void setLicenseEvidenceId(String v){licenseEvidenceId=v;}
    public String getDistributionMode(){return distributionMode;} public void setDistributionMode(String v){distributionMode=v;}
    public String getStatus(){return status;} public void setStatus(String v){status=v;}
    public int getTotalCount(){return totalCount;} public void setTotalCount(int v){totalCount=v;}
    public int getProcessedCount(){return processedCount;} public void setProcessedCount(int v){processedCount=v;}
    public int getReadyCount(){return readyCount;} public void setReadyCount(int v){readyCount=v;}
    public int getDuplicateCount(){return duplicateCount;} public void setDuplicateCount(int v){duplicateCount=v;}
    public int getRejectedCount(){return rejectedCount;} public void setRejectedCount(int v){rejectedCount=v;}
    public int getQuarantinedCount(){return quarantinedCount;} public void setQuarantinedCount(int v){quarantinedCount=v;}
    public int getUnsupportedCount(){return unsupportedCount;} public void setUnsupportedCount(int v){unsupportedCount=v;}
    public String getErrorMessage(){return errorMessage;} public void setErrorMessage(String v){errorMessage=v;}
    public String getRequestedBy(){return requestedBy;} public void setRequestedBy(String v){requestedBy=v;}
    public Instant getStartedAt(){return startedAt;} public void setStartedAt(Instant v){startedAt=v;}
    public Instant getCompletedAt(){return completedAt;} public void setCompletedAt(Instant v){completedAt=v;}
    public Instant getCreatedAt(){return createdAt;} public void setCreatedAt(Instant v){createdAt=v;}
    public Instant getUpdatedAt(){return updatedAt;} public void setUpdatedAt(Instant v){updatedAt=v;}
    public int getVersionNo(){return versionNo;} public void setVersionNo(int v){versionNo=v;}
}

