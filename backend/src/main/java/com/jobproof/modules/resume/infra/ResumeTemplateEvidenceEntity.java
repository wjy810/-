package com.jobproof.modules.resume.infra;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "resume_template_evidence_artifacts")
public class ResumeTemplateEvidenceEntity {
    @Id private String id;
    @Column(name = "evidence_type") private String evidenceType;
    @Column(name = "original_filename") private String originalFilename;
    @Column(name = "content_type") private String contentType;
    @Column(name = "size_bytes") private long sizeBytes;
    @Column(name = "file_hash") private String fileHash;
    @Column(name = "storage_key") private String storageKey;
    private String description;
    @Column(name = "uploaded_by") private String uploadedBy;
    @Column(name = "created_at") private Instant createdAt;

    public String getId() { return id; }
    public void setId(String value) { id = value; }
    public String getEvidenceType() { return evidenceType; }
    public void setEvidenceType(String value) { evidenceType = value; }
    public String getOriginalFilename() { return originalFilename; }
    public void setOriginalFilename(String value) { originalFilename = value; }
    public String getContentType() { return contentType; }
    public void setContentType(String value) { contentType = value; }
    public long getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(long value) { sizeBytes = value; }
    public String getFileHash() { return fileHash; }
    public void setFileHash(String value) { fileHash = value; }
    public String getStorageKey() { return storageKey; }
    public void setStorageKey(String value) { storageKey = value; }
    public String getDescription() { return description; }
    public void setDescription(String value) { description = value; }
    public String getUploadedBy() { return uploadedBy; }
    public void setUploadedBy(String value) { uploadedBy = value; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant value) { createdAt = value; }
}
