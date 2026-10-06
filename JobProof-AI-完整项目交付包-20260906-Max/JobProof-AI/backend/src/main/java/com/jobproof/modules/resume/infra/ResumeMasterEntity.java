package com.jobproof.modules.resume.infra;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "resume_masters")
public class ResumeMasterEntity {

    @Id
    private String id;

    @Column(name = "account_id", nullable = false)
    private String accountId;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "status_before_archive", length = 32)
    private String statusBeforeArchive;

    @Column(nullable = false, length = 32)
    private String source;

    @Column(name = "template_code", length = 32)
    private String templateCode;

    @Column(name = "education_json", columnDefinition = "TEXT")
    private String educationJson;

    @Column(name = "experience_json", columnDefinition = "TEXT")
    private String experienceJson;

    @Column(name = "projects_json", columnDefinition = "TEXT")
    private String projectsJson;

    @Column(name = "skills_json", columnDefinition = "TEXT")
    private String skillsJson;

    @Column(name = "certificates_json", columnDefinition = "TEXT")
    private String certificatesJson;

    @Column(name = "self_intro", columnDefinition = "TEXT")
    private String selfIntro;

    @Column(name = "key_outcomes_json", nullable = false, columnDefinition = "TEXT")
    private String keyOutcomesJson;

    @Column(name = "content_schema_version", nullable = false, length = 32)
    private String contentSchemaVersion = "resume-content-v3";

    @Column(name = "content_json", columnDefinition = "TEXT")
    private String contentJson;

    @Column(name = "version_no", nullable = false)
    private int versionNo;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "archived_at")
    private Instant archivedAt;

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

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getStatusBeforeArchive() {
        return statusBeforeArchive;
    }

    public void setStatusBeforeArchive(String statusBeforeArchive) {
        this.statusBeforeArchive = statusBeforeArchive;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getTemplateCode() {
        return templateCode;
    }

    public void setTemplateCode(String templateCode) {
        this.templateCode = templateCode;
    }

    public String getEducationJson() {
        return educationJson;
    }

    public void setEducationJson(String educationJson) {
        this.educationJson = educationJson;
    }

    public String getExperienceJson() {
        return experienceJson;
    }

    public void setExperienceJson(String experienceJson) {
        this.experienceJson = experienceJson;
    }

    public String getProjectsJson() {
        return projectsJson;
    }

    public void setProjectsJson(String projectsJson) {
        this.projectsJson = projectsJson;
    }

    public String getSkillsJson() {
        return skillsJson;
    }

    public void setSkillsJson(String skillsJson) {
        this.skillsJson = skillsJson;
    }

    public String getCertificatesJson() {
        return certificatesJson;
    }

    public void setCertificatesJson(String certificatesJson) {
        this.certificatesJson = certificatesJson;
    }

    public String getSelfIntro() {
        return selfIntro;
    }

    public void setSelfIntro(String selfIntro) {
        this.selfIntro = selfIntro;
    }

    public String getKeyOutcomesJson() {
        return keyOutcomesJson;
    }

    public void setKeyOutcomesJson(String keyOutcomesJson) {
        this.keyOutcomesJson = keyOutcomesJson;
    }

    public String getContentSchemaVersion() {
        return contentSchemaVersion;
    }

    public void setContentSchemaVersion(String contentSchemaVersion) {
        this.contentSchemaVersion = contentSchemaVersion;
    }

    public String getContentJson() {
        return contentJson;
    }

    public void setContentJson(String contentJson) {
        this.contentJson = contentJson;
    }

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

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Instant getArchivedAt() {
        return archivedAt;
    }

    public void setArchivedAt(Instant archivedAt) {
        this.archivedAt = archivedAt;
    }
}
