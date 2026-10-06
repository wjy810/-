package com.jobproof.modules.resume.infra;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "resume_template_import_items")
public class ResumeTemplateImportItemEntity {
    @Id private String id;
    @Column(name="batch_id") private String batchId;
    @Column(name="source_relative_path") private String sourceRelativePath;
    @Column(name="source_path_hash") private String sourcePathHash;
    @Column(name="original_filename") private String originalFilename;
    @Column(name="size_bytes") private long sizeBytes;
    @Column(name="file_hash") private String fileHash;
    private String status;
    @Column(name="asset_id") private String assetId;
    @Column(name="duplicate_asset_id") private String duplicateAssetId;
    @Column(name="detail_code") private String detailCode;
    @Column(name="created_at") private Instant createdAt;
    @Column(name="updated_at") private Instant updatedAt;
    public String getId(){return id;} public void setId(String v){id=v;}
    public String getBatchId(){return batchId;} public void setBatchId(String v){batchId=v;}
    public String getSourceRelativePath(){return sourceRelativePath;} public void setSourceRelativePath(String v){sourceRelativePath=v;}
    public String getSourcePathHash(){return sourcePathHash;} public void setSourcePathHash(String v){sourcePathHash=v;}
    public String getOriginalFilename(){return originalFilename;} public void setOriginalFilename(String v){originalFilename=v;}
    public long getSizeBytes(){return sizeBytes;} public void setSizeBytes(long v){sizeBytes=v;}
    public String getFileHash(){return fileHash;} public void setFileHash(String v){fileHash=v;}
    public String getStatus(){return status;} public void setStatus(String v){status=v;}
    public String getAssetId(){return assetId;} public void setAssetId(String v){assetId=v;}
    public String getDuplicateAssetId(){return duplicateAssetId;} public void setDuplicateAssetId(String v){duplicateAssetId=v;}
    public String getDetailCode(){return detailCode;} public void setDetailCode(String v){detailCode=v;}
    public Instant getCreatedAt(){return createdAt;} public void setCreatedAt(Instant v){createdAt=v;}
    public Instant getUpdatedAt(){return updatedAt;} public void setUpdatedAt(Instant v){updatedAt=v;}
}
