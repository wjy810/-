package com.jobproof.modules.aibilling.infra;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "wallet_account")
public class WalletAccountEntity {
    @Id private String id;
    @Column(name = "account_id", nullable = false) private String accountId;
    @Column(nullable = false, length = 3) private String currency;
    @Column(name = "available_balance", nullable = false) private BigDecimal availableBalance;
    @Column(name = "held_balance", nullable = false) private BigDecimal heldBalance;
    @Version @Column(name = "version_no", nullable = false) private int versionNo;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    protected WalletAccountEntity() {}
    public String getId(){return id;} public String getAccountId(){return accountId;} public String getCurrency(){return currency;}
    public BigDecimal getAvailableBalance(){return availableBalance;} public BigDecimal getHeldBalance(){return heldBalance;}
    public int getVersionNo(){return versionNo;} public Instant getUpdatedAt(){return updatedAt;}
}
