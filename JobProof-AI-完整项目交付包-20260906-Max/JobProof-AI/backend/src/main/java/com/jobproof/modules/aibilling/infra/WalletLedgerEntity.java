package com.jobproof.modules.aibilling.infra;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity @Table(name="wallet_ledger")
public class WalletLedgerEntity {
 @Id private String id; @Column(name="account_id") private String accountId; @Column(name="ledger_type") private String ledgerType;
 private BigDecimal amount; @Column(name="balance_after") private BigDecimal balanceAfter; @Column(name="reference_type") private String referenceType;
 @Column(name="reference_id") private String referenceId; @Column(name="idempotency_key") private String idempotencyKey; @Column(name="created_at") private Instant createdAt;
 protected WalletLedgerEntity(){} public String getId(){return id;} public String getLedgerType(){return ledgerType;} public BigDecimal getAmount(){return amount;}
 public BigDecimal getBalanceAfter(){return balanceAfter;} public String getReferenceType(){return referenceType;} public String getReferenceId(){return referenceId;}
 public String getIdempotencyKey(){return idempotencyKey;} public Instant getCreatedAt(){return createdAt;}
}
