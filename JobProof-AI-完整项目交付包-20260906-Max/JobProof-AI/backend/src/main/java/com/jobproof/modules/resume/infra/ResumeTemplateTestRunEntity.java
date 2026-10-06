package com.jobproof.modules.resume.infra;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "resume_template_test_runs")
public class ResumeTemplateTestRunEntity {
    @Id private String id;
    @Column(name = "template_version_id") private String templateVersionId;
    @Column(name = "gate_code") private String gateCode;
    private String outcome;
    @Column(name = "evidence_ref") private String evidenceRef;
    @Column(name = "evidence_artifact_id") private String evidenceArtifactId;
    @Column(name = "template_version_no") private int templateVersionNo;
    @Column(name = "executed_by") private String executedBy;
    @Column(name = "environment_json", columnDefinition = "LONGTEXT") private String environmentJson;
    @Column(name = "report_json", columnDefinition = "LONGTEXT") private String reportJson;
    @Column(name = "created_at") private Instant createdAt;

    public String getId() { return id; }
    public void setId(String value) { id = value; }
    public String getTemplateVersionId() { return templateVersionId; }
    public void setTemplateVersionId(String value) { templateVersionId = value; }
    public String getGateCode() { return gateCode; }
    public void setGateCode(String value) { gateCode = value; }
    public String getOutcome() { return outcome; }
    public void setOutcome(String value) { outcome = value; }
    public String getEvidenceRef() { return evidenceRef; }
    public void setEvidenceRef(String value) { evidenceRef = value; }
    public String getEvidenceArtifactId() { return evidenceArtifactId; }
    public void setEvidenceArtifactId(String value) { evidenceArtifactId = value; }
    public int getTemplateVersionNo() { return templateVersionNo; }
    public void setTemplateVersionNo(int value) { templateVersionNo = value; }
    public String getExecutedBy() { return executedBy; }
    public void setExecutedBy(String value) { executedBy = value; }
    public String getEnvironmentJson() { return environmentJson; }
    public void setEnvironmentJson(String value) { environmentJson = value; }
    public String getReportJson() { return reportJson; }
    public void setReportJson(String value) { reportJson = value; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant value) { createdAt = value; }
}
