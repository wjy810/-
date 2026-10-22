package com.jobproof;

import static org.assertj.core.api.Assertions.*;
import com.jobproof.modules.audit.application.AuditQueryService;
import com.jobproof.modules.audit.application.AuditService;
import com.jobproof.shared.auth.CurrentAccount;
import com.jobproof.shared.error.AppException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest @ActiveProfiles("test")
class AdminAuditIT {
    @Autowired AuditQueryService query;
    @Autowired AuditService audit;
    @Autowired JdbcTemplate jdbc;
    @Test void filtersLegacyEventsAndMasksSecretsWithoutGrantingSeekersAccess() {
        String objectId = UUID.randomUUID().toString();
        var admin = new CurrentAccount(UUID.randomUUID().toString(), "audit@example.com", "ADMIN", "test");
        audit.append(admin.accountId(), "KEY_CHANGED", "CHANNEL", objectId, "apiKey=very-secret sk-sensitive JP-AAAA-BBBB-CCCC-DDDD");
        jdbc.update("INSERT INTO ai_audit_log(id,action,object_type,object_id,result,summary,created_at) VALUES(?,'KEY_CHANGED','CHANNEL',?,'SUCCESS','Authorization: Bearer DEMO_TOKEN_ABC',?)", UUID.randomUUID().toString(), objectId, Instant.now());
        var page = query.list(admin, null, "KEY_CHANGED", "CHANNEL", objectId, Instant.now().minusSeconds(60), Instant.now().plusSeconds(60), 0, 1);
        assertThat(page.total()).isEqualTo(2);
        assertThat(page.items()).hasSize(1);
        var all = query.list(admin, null, null, null, objectId, null, null, 0, 20);
        assertThat(all.items()).allSatisfy(e -> assertThat(e.summary()).doesNotContain("very-secret", "sk-sensitive", "AAAA", "password=secret", "DEMO_TOKEN_ABC"));
        assertThatThrownBy(() -> query.list(new CurrentAccount("seeker", "s@test.com", "SEEKER", "test"), null,null,null,null,null,null,0,20)).isInstanceOf(AppException.class);
    }
}
