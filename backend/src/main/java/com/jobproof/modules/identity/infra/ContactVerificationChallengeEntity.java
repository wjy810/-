package com.jobproof.modules.identity.infra;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "contact_verification_challenges")
public class ContactVerificationChallengeEntity {

    @Id
    private String id;

    @Column(nullable = false, length = 16)
    private String channel;

    @Column(name = "destination_hash", nullable = false, length = 64)
    private String destinationHash;

    @Column(name = "destination_masked", nullable = false, length = 128)
    private String destinationMasked;

    @Column(nullable = false, length = 32)
    private String purpose;

    @Column(name = "provider_code", nullable = false, length = 32)
    private String providerCode;

    @Column(name = "code_hash", length = 64)
    private String codeHash;

    @Column(name = "verification_mode", nullable = false, length = 32)
    private String verificationMode;

    @Column(name = "provider_request_id", length = 128)
    private String providerRequestId;

    @Column(name = "request_ip_hash", length = 64)
    private String requestIpHash;

    @Column(name = "verification_token_hash", length = 64)
    private String verificationTokenHash;

    @Column(name = "verification_token_expires_at")
    private Instant verificationTokenExpiresAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }
    public String getDestinationHash() { return destinationHash; }
    public void setDestinationHash(String destinationHash) { this.destinationHash = destinationHash; }
    public String getDestinationMasked() { return destinationMasked; }
    public void setDestinationMasked(String destinationMasked) { this.destinationMasked = destinationMasked; }
    public String getPurpose() { return purpose; }
    public void setPurpose(String purpose) { this.purpose = purpose; }
    public String getProviderCode() { return providerCode; }
    public void setProviderCode(String providerCode) { this.providerCode = providerCode; }
    public String getCodeHash() { return codeHash; }
    public void setCodeHash(String codeHash) { this.codeHash = codeHash; }
    public String getVerificationMode() { return verificationMode; }
    public void setVerificationMode(String value) { this.verificationMode = value; }
    public String getProviderRequestId() { return providerRequestId; }
    public void setProviderRequestId(String value) { this.providerRequestId = value; }
    public String getRequestIpHash() { return requestIpHash; }
    public void setRequestIpHash(String value) { this.requestIpHash = value; }
    public String getVerificationTokenHash() { return verificationTokenHash; }
    public void setVerificationTokenHash(String verificationTokenHash) { this.verificationTokenHash = verificationTokenHash; }
    public Instant getVerificationTokenExpiresAt() { return verificationTokenExpiresAt; }
    public void setVerificationTokenExpiresAt(Instant value) { this.verificationTokenExpiresAt = value; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public Instant getVerifiedAt() { return verifiedAt; }
    public void setVerifiedAt(Instant verifiedAt) { this.verifiedAt = verifiedAt; }
    public Instant getConsumedAt() { return consumedAt; }
    public void setConsumedAt(Instant consumedAt) { this.consumedAt = consumedAt; }
    public int getFailedAttempts() { return failedAttempts; }
    public void setFailedAttempts(int failedAttempts) { this.failedAttempts = failedAttempts; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
