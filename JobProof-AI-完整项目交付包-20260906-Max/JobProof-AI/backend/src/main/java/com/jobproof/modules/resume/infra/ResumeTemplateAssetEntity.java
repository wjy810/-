package com.jobproof.modules.resume.infra;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "resume_template_assets")
public class ResumeTemplateAssetEntity {
    @Id private String id;
    @Column(name = "source_name") private String sourceName;
    @Column(name = "source_uri") private String sourceUri;
    @Column(name = "original_filename") private String originalFilename;
    @Column(name = "source_relative_path") private String sourceRelativePath;
    @Column(name = "import_batch_id") private String importBatchId;
    @Column(name = "license_status") private String licenseStatus;
    @Column(name = "license_evidence") private String licenseEvidence;
    @Column(name = "license_evidence_id") private String licenseEvidenceId;
    @Column(name = "file_hash") private String fileHash;
    @Column(name = "storage_key") private String storageKey;
    @Column(name = "content_type") private String contentType;
    @Column(name = "size_bytes") private Long sizeBytes;
    @Column(name = "scan_status") private String scanStatus;
    @Column(name = "scan_report_json", columnDefinition = "TEXT") private String scanReportJson;
    @Column(name = "scanned_at") private Instant scannedAt;
    @Column(name = "uploaded_by") private String uploadedBy;
    @Column(name = "reviewed_by") private String reviewedBy;
    @Column(name = "reviewed_at") private Instant reviewedAt;
    private String status;
    @Column(name = "rejection_reason") private String rejectionReason;
    @Column(name = "version_no") private int versionNo;
    @Column(name = "created_at") private Instant createdAt;
    @Column(name = "updated_at") private Instant updatedAt;
    public String getId(){return id;} public void setId(String v){id=v;} public String getSourceName(){return sourceName;} public void setSourceName(String v){sourceName=v;}
    public String getSourceUri(){return sourceUri;} public void setSourceUri(String v){sourceUri=v;} public String getLicenseStatus(){return licenseStatus;} public void setLicenseStatus(String v){licenseStatus=v;}
    public String getOriginalFilename(){return originalFilename;} public void setOriginalFilename(String v){originalFilename=v;} public String getSourceRelativePath(){return sourceRelativePath;} public void setSourceRelativePath(String v){sourceRelativePath=v;}
    public String getImportBatchId(){return importBatchId;} public void setImportBatchId(String v){importBatchId=v;}
    public String getLicenseEvidence(){return licenseEvidence;} public void setLicenseEvidence(String v){licenseEvidence=v;} public String getFileHash(){return fileHash;} public void setFileHash(String v){fileHash=v;}
    public String getLicenseEvidenceId(){return licenseEvidenceId;} public void setLicenseEvidenceId(String v){licenseEvidenceId=v;}
    public String getStorageKey(){return storageKey;} public void setStorageKey(String v){storageKey=v;} public String getContentType(){return contentType;} public void setContentType(String v){contentType=v;}
    public Long getSizeBytes(){return sizeBytes;} public void setSizeBytes(Long v){sizeBytes=v;} public String getScanStatus(){return scanStatus;} public void setScanStatus(String v){scanStatus=v;}
    public String getScanReportJson(){return scanReportJson;} public void setScanReportJson(String v){scanReportJson=v;} public Instant getScannedAt(){return scannedAt;} public void setScannedAt(Instant v){scannedAt=v;}
    public String getUploadedBy(){return uploadedBy;} public void setUploadedBy(String v){uploadedBy=v;}
    public String getReviewedBy(){return reviewedBy;} public void setReviewedBy(String v){reviewedBy=v;} public Instant getReviewedAt(){return reviewedAt;} public void setReviewedAt(Instant v){reviewedAt=v;}
    public String getStatus(){return status;} public void setStatus(String v){status=v;} public String getRejectionReason(){return rejectionReason;} public void setRejectionReason(String v){rejectionReason=v;}
    public int getVersionNo(){return versionNo;} public void setVersionNo(int v){versionNo=v;} public Instant getCreatedAt(){return createdAt;} public void setCreatedAt(Instant v){createdAt=v;}
    public Instant getUpdatedAt(){return updatedAt;} public void setUpdatedAt(Instant v){updatedAt=v;}
}
