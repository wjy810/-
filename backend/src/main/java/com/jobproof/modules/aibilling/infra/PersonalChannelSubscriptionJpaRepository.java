package com.jobproof.modules.aibilling.infra;
import java.util.Optional; import org.springframework.data.jpa.repository.JpaRepository;
public interface PersonalChannelSubscriptionJpaRepository extends JpaRepository<PersonalChannelSubscriptionEntity,String>{Optional<PersonalChannelSubscriptionEntity> findByAccountId(String accountId);}
