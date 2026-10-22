package com.jobproof;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobproof.modules.aiconfig.application.AiAdminChannelService;
import com.jobproof.modules.aiconfig.application.AiAdminChannelService.*;
import com.jobproof.modules.aiconfig.application.AiUserChannelService;
import com.jobproof.modules.aiconfig.application.AiUserChannelService.*;
import com.jobproof.modules.aigateway.domain.AiGatewayModels.Channel;
import com.jobproof.modules.aigateway.port.AiChannelPort;
import com.jobproof.modules.aigateway.security.ApiKeyCipher;
import com.jobproof.modules.aigateway.security.SsrfGuard;
import com.jobproof.shared.ai.AiExecutionContext;
import com.jobproof.shared.auth.CurrentAccount;
import com.jobproof.shared.error.AppException;
import com.jobproof.shared.id.Ids;
import com.jobproof.shared.time.ClockPort;
import com.jobproof.support.AiProviderStub;
import jakarta.persistence.EntityManager;
import java.net.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.*;

@SpringBootTest
@ActiveProfiles("test")
@Import(AiChannelManagementIT.Configuration.class)
@Transactional
class AiChannelManagementIT {
  static final AtomicReference<Instant> NOW =
      new AtomicReference<>(Instant.parse("2026-10-21T01:00:00Z"));

  @TestConfiguration
  static class Configuration {
    @Bean
    @Primary
    ClockPort channelTestClock() {
      return NOW::get;
    }

    @Bean
    @Primary
    KeyAwareProvider channelTestProvider() {
      return new KeyAwareProvider();
    }

    @Bean
    @Primary
    SsrfGuard channelTestDns() {
      return new SsrfGuard(host -> new InetAddress[] {InetAddress.getByName("93.184.216.34")});
    }
  }

  static class KeyAwareProvider extends AiProviderStub {
    @Override
    public TransportResponse exchange(
        URI uri, String method, Map<String, String> headers, JsonNode body, Duration timeout) {
      if (headers.values().stream().anyMatch(v -> v.contains("replacement-bad-key")))
        return new TransportResponse(401, Map.of(), new ObjectMapper().createObjectNode());
      return super.exchange(uri, method, headers, body, timeout);
    }
  }

  @Autowired AiAdminChannelService admin;
  @Autowired AiUserChannelService personal;
  @Autowired AiChannelPort routing;
  @Autowired JdbcTemplate jdbc;
  @Autowired EntityManager entities;
  @Autowired ApiKeyCipher cipher;
  @Autowired ObjectMapper json;
  @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;

  @BeforeEach
  void reset() {
    NOW.set(Instant.parse("2026-10-21T01:00:00Z"));
    AiExecutionContext.clear();
  }

  @AfterEach
  void cleanup() {
    AiExecutionContext.clear();
  }

  @Test
  void staleKeyTestCannotActivateDraftAndFailedDraftPreservesLiveConfiguration() {
    var owner = account("ADMIN");
    var channel = system(owner);
    channel = activateSystem(owner, channel, "working-model");
    var changed =
        admin.update(
            owner,
            channel.id(),
            new UpdateChannelCommand(
                "Renamed",
                "",
                "https://next.example",
                "replacement-bad-key",
                "OPENAI_CHAT",
                720,
                "working-model",
                channel.versionNo()));
    assertThat(changed.name()).isEqualTo("Renamed");
    assertThat(changed.baseUrl()).isEqualTo("https://provider.example/v1");
    assertThat(changed.pending().baseUrl()).isEqualTo("https://next.example");
    assertThat(changed.apiKeyMasked()).isEqualTo("****-key");
    assertThat(changed.pending().apiKeyMasked()).isEqualTo("****-key");
    assertThatThrownBy(
            () -> admin.activate(owner, changed.id(), changed.versionNo(), "working-model"))
        .isInstanceOf(AppException.class)
        .hasMessageContaining("重新检测");
    var failed = admin.capabilityTest(owner, channel.id(), "working-model", null);
    assertThat(failed.status()).isEqualTo("FAILED");
    var retained =
        admin.list(owner).stream()
            .filter(c -> c.id().equals(changed.id()))
            .findFirst()
            .orElseThrow();
    assertThat(retained.status()).isEqualTo("ACTIVE");
    assertThat(retained.healthStatus()).isEqualTo("HEALTHY");
    var live =
        routing.findEligible(owner.accountId(), "auto").stream()
            .filter(c -> c.id().equals(changed.id()))
            .findFirst()
            .orElseThrow();
    assertThat(live.baseUri().toString()).isEqualTo("https://provider.example/v1");
    assertThat(cipher.decrypt(live.encryptedApiKey())).isEqualTo("original-test-key");
    assertThat(live.weight()).isEqualTo(720);
    var repaired =
        admin.update(
            owner,
            retained.id(),
            new UpdateChannelCommand(
                "Renamed",
                "",
                "https://next.example",
                "replacement-good-key",
                "OPENAI_CHAT",
                720,
                "working-model",
                retained.versionNo()));
    var applied = activateSystem(owner, repaired, "working-model");
    assertThat(applied.pending()).isNull();
    assertThat(applied.baseUrl()).isEqualTo("https://next.example/v1");
    assertThat(
            cipher.decrypt(
                routing.findEligible(owner.accountId(), "auto").stream()
                    .filter(c -> c.id().equals(applied.id()))
                    .findFirst()
                    .orElseThrow()
                    .encryptedApiKey()))
        .isEqualTo("replacement-good-key");
    assertThat(
            jdbc.queryForList(
                    "SELECT summary FROM audit_events WHERE object_id=?",
                    String.class,
                    channel.id())
                .toString())
        .doesNotContain("original-test-key", "replacement-good-key", "replacement-bad-key");
  }

  @Test
  void blankKeyKeepsCiphertextAndMaskIsRejected() {
    var owner = account("ADMIN");
    var channel = system(owner);
    String original =
        jdbc.queryForObject(
            "SELECT api_key_ciphertext FROM ai_channel WHERE id=?", String.class, channel.id());
    var edited =
        admin.update(
            owner,
            channel.id(),
            new UpdateChannelCommand(
                "New name",
                null,
                channel.baseUrl(),
                " ",
                channel.protocol(),
                55,
                null,
                channel.versionNo()));
    assertThat(edited.configVersion()).isZero();
    assertThat(edited.routingWeight()).isEqualTo(55);
    assertThat(
            jdbc.queryForObject(
                "SELECT api_key_ciphertext FROM ai_channel WHERE id=?", String.class, channel.id()))
        .isEqualTo(original);
    assertThatThrownBy(
            () ->
                admin.update(
                    owner,
                    channel.id(),
                    new UpdateChannelCommand(
                        "New name",
                        null,
                        channel.baseUrl(),
                        "****abcd",
                        channel.protocol(),
                        55,
                        null,
                        edited.versionNo())))
        .hasMessageContaining("掩码");
  }

  @Test
  void sameTimestampRetestUsesExactLatestTestAndConcreteModel() {
    var owner = account("ADMIN");
    var channel = system(owner);
    admin.capabilityTest(owner, channel.id(), "working-model", null);
    admin.capabilityTest(owner, channel.id(), "bad-json", null);
    var latest =
        admin.list(owner).stream()
            .filter(c -> c.id().equals(channel.id()))
            .findFirst()
            .orElseThrow();
    assertThat(latest.capability().testedModel()).isEqualTo("bad-json");
    assertThatThrownBy(
            () -> admin.activate(owner, channel.id(), latest.versionNo(), "working-model"))
        .hasMessageContaining("重新检测");
    var passed = activateSystem(owner, latest, "unused-model");
    assertThat(passed.selectedModel()).isEqualTo("unused-model");
  }

  @Test
  void personalSubscriptionLimitArchivalAndPersistedIdempotency() throws Exception {
    var owner = account("SEEKER");
    assertThat(personal.preference(owner).source()).isEqualTo("PLATFORM");
    assertThatThrownBy(() -> personal.create(owner, create("Blocked", "blocked")))
        .hasMessageContaining("订阅");
    subscribe(owner);
    var command = create("One", "same-create");
    var first = personal.create(owner, command);
    assertThat(personal.create(owner, command).id()).isEqualTo(first.id());
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM ai_channel_commands WHERE account_id=? AND"
                    + " operation='CREATE'",
                Integer.class,
                owner.accountId()))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT request_hash FROM ai_channel_commands WHERE account_id=? AND"
                    + " operation='CREATE'",
                String.class,
                owner.accountId()))
        .hasSize(64)
        .doesNotContain("original-test-key");
    assertThatThrownBy(() -> personal.create(owner, create("Different", "same-create")))
        .hasMessageContaining("幂等键");
    for (int i = 2; i <= 5; i++) personal.create(owner, create("Channel " + i, "create-" + i));
    assertThatThrownBy(() -> personal.create(owner, create("Six", "six")))
        .hasMessageContaining("最多保存 5");
    personal.archive(owner, first.id(), first.versionNo(), "archive");
    personal.create(owner, create("Six", "six"));
    assertThat(personal.list(owner, "PERSONAL", null, PageRequest.of(0, 100)).getTotalElements())
        .isEqualTo(6);
    assertThat(
            json.writeValueAsString(personal.list(owner, "PERSONAL", null, PageRequest.of(0, 100))))
        .doesNotContain("original-test-key", "apiKeyCiphertext");
  }

  @Test
  void personalOwnerIsolationExplicitSelectionAndNoPoolMixing() {
    var owner = account("SEEKER");
    var other = account("SEEKER");
    subscribe(owner);
    subscribe(other);
    var one = activatePersonal(owner, personal.create(owner, create("One", "one")));
    var two = activatePersonal(owner, personal.create(owner, create("Two", "two")));
    var foreign = activatePersonal(other, personal.create(other, create("Other", "other")));
    assertThat(personal.list(other, "PERSONAL", null, PageRequest.of(0, 20)).getContent())
        .extracting(SystemChannelView::id)
        .containsExactly(foreign.id());
    assertThatThrownBy(() -> personal.test(other, one.id(), "working-model", "probe"))
        .hasMessageContaining("他人");
    assertThatThrownBy(
            () ->
                personal.select(
                    owner, new PreferenceCommand(foreign.id(), false, 0, "select-foreign")))
        .hasMessageContaining("他人");
    var selection = personal.select(owner, new PreferenceCommand(two.id(), false, 0, "select-two"));
    assertThat(routing.findAvailable(owner.accountId(), "auto"))
        .extracting(Channel::id)
        .containsExactly(two.id());
    assertThatThrownBy(() -> routing.findEligible(owner.accountId(), "auto"))
        .hasMessageContaining("重新确认");
    try (var ignored =
        AiExecutionContext.use(
            new AiExecutionContext.State(
                null, null, null, false, owner.accountId(), "reservation", one.id()))) {
      assertThat(routing.findEligible(owner.accountId(), "some-business-model"))
          .extracting(Channel::id)
          .containsExactly(one.id());
      assertThatThrownBy(() -> routing.findEligible(other.accountId(), "auto"))
          .hasMessageContaining("账号不匹配");
    }
    assertThatThrownBy(
            () ->
                personal.select(
                    owner,
                    new PreferenceCommand(
                        null, false, selection.versionNo(), "platform-unconfirmed")))
        .hasMessageContaining("明确确认");
    var platform =
        personal.select(
            owner, new PreferenceCommand(null, true, selection.versionNo(), "platform-confirmed"));
    assertThat(platform.source()).isEqualTo("PLATFORM");
    assertThat(
            personal
                .select(
                    owner,
                    new PreferenceCommand(null, true, selection.versionNo(), "platform-confirmed"))
                .versionNo())
        .isEqualTo(platform.versionNo());
    assertThatThrownBy(
            () ->
                personal.select(
                    owner, new PreferenceCommand(two.id(), false, selection.versionNo(), "stale")))
        .hasMessageContaining("刷新");
  }

  @Test
  void expiryKeepsConfigurationHistoryAndSelectionButFailsUseAndEnableClosed() {
    var owner = account("SEEKER");
    subscribe(owner);
    var channel = activatePersonal(owner, personal.create(owner, create("Kept", "keep")));
    personal.select(owner, new PreferenceCommand(channel.id(), false, 0, "select"));
    NOW.set(NOW.get().plusSeconds(3600));
    assertThat(personal.preference(owner).subscriptionActive()).isFalse();
    assertThat(personal.preference(owner).personalChannelId()).isEqualTo(channel.id());
    assertThat(personal.list(owner, "PERSONAL", null, PageRequest.of(0, 20)).getContent())
        .hasSize(1);
    assertThatThrownBy(() -> personal.create(owner, create("Expired", "expired")))
        .hasMessageContaining("订阅");
    assertThatThrownBy(() -> personal.test(owner, channel.id(), "working-model", "expired-test"))
        .hasMessageContaining("订阅");
    assertThatThrownBy(
            () ->
                personal.activate(
                    owner, channel.id(), channel.versionNo(), "working-model", "expired-enable"))
        .hasMessageContaining("订阅");
    assertThatThrownBy(() -> routing.findAvailable(owner.accountId(), "auto"))
        .hasMessageContaining("订阅已到期");
    try (var ignored =
        AiExecutionContext.use(
            new AiExecutionContext.State(
                null, null, null, false, owner.accountId(), "reservation", channel.id()))) {
      assertThatThrownBy(() -> routing.findEligible(owner.accountId(), "auto"))
          .hasMessageContaining("订阅已到期");
    }
    var updated =
        personal.update(
            owner,
            channel.id(),
            new UpdateCommand(
                "Still retained",
                null,
                channel.baseUrl(),
                "",
                channel.protocol(),
                null,
                null,
                channel.versionNo(),
                "expired-edit"));
    assertThat(updated.name()).isEqualTo("Still retained");
    personal.disable(owner, channel.id(), updated.versionNo(), "expired-disable");
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM ai_channel_capability_test WHERE channel_id=?",
                Integer.class,
                channel.id()))
        .isPositive();
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void concurrentCreatesCannotExceedFiveAndConcurrentRetriesPersistOnce() throws Exception {
    var owner = account("SEEKER");
    subscribe(owner);
    for (int i = 0; i < 4; i++) personal.create(owner, create("Seed " + i, "seed-" + i));
    try (var pool = Executors.newFixedThreadPool(2)) {
      var start = new CountDownLatch(1);
      Callable<Boolean> first =
          () -> {
            start.await();
            try {
              personal.create(owner, create("Final A", "final-a"));
              return true;
            } catch (AppException e) {
              return false;
            }
          };
      Callable<Boolean> second =
          () -> {
            start.await();
            try {
              personal.create(owner, create("Final B", "final-b"));
              return true;
            } catch (AppException e) {
              return false;
            }
          };
      var a = pool.submit(first);
      var b = pool.submit(second);
      start.countDown();
      assertThat(List.of(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(true, false);
    }
    assertThat(personal.list(owner, "PERSONAL", null, PageRequest.of(0, 20)).getTotalElements())
        .isEqualTo(5);
    var other = account("SEEKER");
    subscribe(other);
    var command = create("Same", "repeat");
    try (var pool = Executors.newFixedThreadPool(2)) {
      var a = pool.submit(() -> personal.create(other, command));
      var b = pool.submit(() -> personal.create(other, command));
      assertThat(a.get(15, TimeUnit.SECONDS).id()).isEqualTo(b.get(15, TimeUnit.SECONDS).id());
    }
    assertThat(personal.list(other, "PERSONAL", null, PageRequest.of(0, 20)).getTotalElements())
        .isEqualTo(1);
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void admissionAndConcurrentDisableUseAccountFirstAndExecutionFailsClosed() throws Exception {
    var owner = account("SEEKER");
    subscribe(owner);
    var channel = activatePersonal(owner, personal.create(owner, create("Lock order", "lock-create")));
    personal.select(owner, new PreferenceCommand(channel.id(), false, 0, "lock-select"));
    var available = new CountDownLatch(1);
    var editing = new CountDownLatch(1);
    var continueAdmission = new CountDownLatch(1);
    var transactions = new org.springframework.transaction.support.TransactionTemplate(transactionManager);
    try (var pool = Executors.newFixedThreadPool(2)) {
      var admission = pool.submit(() -> transactions.execute(status -> {
        assertThat(routing.findAvailable(owner.accountId(), "auto")).hasSize(1);
        available.countDown();
        try { assertThat(continueAdmission.await(10, TimeUnit.SECONDS)).isTrue(); }
        catch (InterruptedException e) { throw new RuntimeException(e); }
        // The reserve boundary takes the same account lock after availability.
        jdbc.queryForList("SELECT id FROM accounts WHERE id=? FOR UPDATE", owner.accountId());
        return true;
      }));
      assertThat(available.await(10, TimeUnit.SECONDS)).isTrue();
      var disable = pool.submit(() -> {
        editing.countDown();
        return personal.disable(owner, channel.id(), channel.versionNo(), "lock-disable");
      });
      assertThat(editing.await(10, TimeUnit.SECONDS)).isTrue();
      continueAdmission.countDown();
      assertThat(admission.get(10, TimeUnit.SECONDS)).isTrue();
      disable.get(10, TimeUnit.SECONDS);
      try (var scope = AiExecutionContext.use(new AiExecutionContext.State(
          null, null, null, false, owner.accountId(), "bound-reservation", channel.id()))) {
        assertThatThrownBy(() -> routing.findEligible(owner.accountId(), "auto"))
            .hasMessageContaining("不可用");
      }
    } finally { continueAdmission.countDown(); }
  }

  private CurrentAccount account(String role) {
    String id = Ids.newId();
    jdbc.update(
        "INSERT INTO"
            + " accounts(id,email,password_hash,status,role,created_at,updated_at,password_changed_at)"
            + " VALUES(?,?,'test','ACTIVE',?,?,?,?)",
        id,
        id + "@example.test",
        role,
        NOW.get(),
        NOW.get(),
        NOW.get());
    return new CurrentAccount(id, id + "@example.test", role, "");
  }

  private void subscribe(CurrentAccount owner) {
    jdbc.update(
        "INSERT INTO"
            + " personal_channel_subscription(id,account_id,status,price,currency,current_period_start,current_period_end,auto_renew,idempotency_key,created_at,updated_at)"
            + " VALUES(?,?,'ACTIVE',1,'CNY',?,?,FALSE,'test',?,?)",
        Ids.newId(),
        owner.accountId(),
        NOW.get(),
        NOW.get().plusSeconds(3600),
        NOW.get(),
        NOW.get());
  }

  private CreateCommand create(String name, String key) {
    return new CreateCommand(
        name, "", "https://provider.example/v1", "original-test-key", "OPENAI_CHAT", key);
  }

  private SystemChannelView system(CurrentAccount owner) {
    return admin.create(
        owner,
        new CreateSystemChannelCommand(
            "System " + Ids.newId(),
            "",
            "https://provider.example/v1",
            "original-test-key",
            "OPENAI_CHAT",
            100));
  }

  private SystemChannelView activateSystem(
      CurrentAccount owner, SystemChannelView channel, String model) {
    admin.capabilityTest(owner, channel.id(), model, null);
    var fresh =
        admin.list(owner).stream()
            .filter(c -> c.id().equals(channel.id()))
            .findFirst()
            .orElseThrow();
    return admin.activate(owner, channel.id(), fresh.versionNo(), model);
  }

  private SystemChannelView activatePersonal(CurrentAccount owner, SystemChannelView channel) {
    personal.test(owner, channel.id(), "working-model", Ids.newId());
    var fresh =
        personal.list(owner, "PERSONAL", null, PageRequest.of(0, 100)).stream()
            .filter(c -> c.id().equals(channel.id()))
            .findFirst()
            .orElseThrow();
    return personal.activate(owner, channel.id(), fresh.versionNo(), "working-model", Ids.newId());
  }
}
