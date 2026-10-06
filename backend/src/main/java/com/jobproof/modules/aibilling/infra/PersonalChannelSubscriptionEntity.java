package com.jobproof.modules.aibilling.infra;
import jakarta.persistence.*; import java.math.BigDecimal; import java.time.Instant;
@Entity @Table(name="personal_channel_subscription") public class PersonalChannelSubscriptionEntity {
 @Id private String id; @Column(name="account_id") private String accountId; private String status; private BigDecimal price; private String currency; @Column(name="current_period_start") private Instant currentPeriodStart; @Column(name="current_period_end") private Instant currentPeriodEnd; @Column(name="auto_renew") private boolean autoRenew; @Version @Column(name="version_no") private int versionNo;
 protected PersonalChannelSubscriptionEntity(){} public String getId(){return id;} public String getStatus(){return status;} public BigDecimal getPrice(){return price;} public String getCurrency(){return currency;} public Instant getCurrentPeriodStart(){return currentPeriodStart;} public Instant getCurrentPeriodEnd(){return currentPeriodEnd;} public boolean isAutoRenew(){return autoRenew;} public int getVersionNo(){return versionNo;}
}
