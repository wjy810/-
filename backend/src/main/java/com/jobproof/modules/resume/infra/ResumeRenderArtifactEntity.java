package com.jobproof.modules.resume.infra;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "resume_render_artifacts")
public class ResumeRenderArtifactEntity {
    @Id private String id;
    @Column(name = "account_id") private String accountId;
    @Column(name = "layout_instance_id") private String layoutInstanceId;
    private String format;
    private String status;
    @Column(name = "renderer_version") private String rendererVersion;
    @Column(name = "content_version_id") private String contentVersionId;
    @Column(name = "template_version_id") private String templateVersionId;
    @Column(name = "file_id") private String fileId;
    @Column(name = "file_hash") private String fileHash;
    @Column(name = "validation_json", columnDefinition = "LONGTEXT") private String validationJson;
    @Column(name = "failure_code") private String failureCode;
    @Column(name = "failure_message") private String failureMessage;
    @Column(name = "created_at") private Instant createdAt;
    @Column(name = "updated_at") private Instant updatedAt;
    public String getId(){return id;} public void setId(String v){id=v;} public String getAccountId(){return accountId;} public void setAccountId(String v){accountId=v;}
    public String getLayoutInstanceId(){return layoutInstanceId;} public void setLayoutInstanceId(String v){layoutInstanceId=v;} public String getFormat(){return format;} public void setFormat(String v){format=v;}
    public String getStatus(){return status;} public void setStatus(String v){status=v;} public String getRendererVersion(){return rendererVersion;} public void setRendererVersion(String v){rendererVersion=v;}
    public String getContentVersionId(){return contentVersionId;} public void setContentVersionId(String v){contentVersionId=v;} public String getTemplateVersionId(){return templateVersionId;} public void setTemplateVersionId(String v){templateVersionId=v;}
    public String getFileId(){return fileId;} public void setFileId(String v){fileId=v;} public String getFileHash(){return fileHash;} public void setFileHash(String v){fileHash=v;}
    public String getValidationJson(){return validationJson;} public void setValidationJson(String v){validationJson=v;} public String getFailureCode(){return failureCode;} public void setFailureCode(String v){failureCode=v;}
    public String getFailureMessage(){return failureMessage;} public void setFailureMessage(String v){failureMessage=v;} public Instant getCreatedAt(){return createdAt;} public void setCreatedAt(Instant v){createdAt=v;}
    public Instant getUpdatedAt(){return updatedAt;} public void setUpdatedAt(Instant v){updatedAt=v;}
}
