package com.jobproof.modules.resume.infra;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "resume_layout_instances")
public class ResumeLayoutInstanceEntity {
    @Id private String id;
    @Column(name = "account_id") private String accountId;
    @Column(name = "master_id") private String masterId;
    @Column(name = "branch_id") private String branchId;
    @Column(name = "content_version_id") private String contentVersionId;
    @Column(name = "template_version_id") private String templateVersionId;
    @Column(name = "variant_code") private String variantCode;
    private String status;
    @Column(name = "overflow_json", columnDefinition = "LONGTEXT") private String overflowJson;
    @Column(name = "content_snapshot_json", columnDefinition = "LONGTEXT") private String contentSnapshotJson;
    @Column(name = "template_snapshot_json", columnDefinition = "LONGTEXT") private String templateSnapshotJson;
    @Column(name = "design_schema_version") private String designSchemaVersion;
    @Column(name = "design_json", columnDefinition = "LONGTEXT") private String designJson;
    @Column(name = "version_no") private int versionNo;
    @Column(name = "created_at") private Instant createdAt;
    @Column(name = "updated_at") private Instant updatedAt;
    @Column(name = "frozen_at") private Instant frozenAt;
    @Column(name = "archived_at") private Instant archivedAt;
    public String getId(){return id;} public void setId(String v){id=v;}
    public String getAccountId(){return accountId;} public void setAccountId(String v){accountId=v;}
    public String getMasterId(){return masterId;} public void setMasterId(String v){masterId=v;}
    public String getBranchId(){return branchId;} public void setBranchId(String v){branchId=v;}
    public String getContentVersionId(){return contentVersionId;} public void setContentVersionId(String v){contentVersionId=v;}
    public String getTemplateVersionId(){return templateVersionId;} public void setTemplateVersionId(String v){templateVersionId=v;}
    public String getVariantCode(){return variantCode;} public void setVariantCode(String v){variantCode=v;}
    public String getStatus(){return status;} public void setStatus(String v){status=v;}
    public String getOverflowJson(){return overflowJson;} public void setOverflowJson(String v){overflowJson=v;}
    public String getContentSnapshotJson(){return contentSnapshotJson;} public void setContentSnapshotJson(String v){contentSnapshotJson=v;}
    public String getTemplateSnapshotJson(){return templateSnapshotJson;} public void setTemplateSnapshotJson(String v){templateSnapshotJson=v;}
    public String getDesignSchemaVersion(){return designSchemaVersion;} public void setDesignSchemaVersion(String v){designSchemaVersion=v;}
    public String getDesignJson(){return designJson;} public void setDesignJson(String v){designJson=v;}
    public int getVersionNo(){return versionNo;} public void setVersionNo(int v){versionNo=v;}
    public Instant getCreatedAt(){return createdAt;} public void setCreatedAt(Instant v){createdAt=v;}
    public Instant getUpdatedAt(){return updatedAt;} public void setUpdatedAt(Instant v){updatedAt=v;}
    public Instant getFrozenAt(){return frozenAt;} public void setFrozenAt(Instant v){frozenAt=v;}
    public Instant getArchivedAt(){return archivedAt;} public void setArchivedAt(Instant v){archivedAt=v;}
}
