package com.jobproof.modules.audit.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;

import com.jobproof.modules.audit.infra.AuditEventEntity;
import com.jobproof.modules.audit.infra.AuditEventJpaRepository;
import com.jobproof.shared.time.ClockPort;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuditServiceAppendTest {

    @Mock
    AuditEventJpaRepository repository;

    @Test
    void appendPersistsSanitizedSummaryWithoutPlainSecrets() {
        ClockPort clock = () -> Instant.parse("2026-08-19T00:00:00Z");
        AuditService service = new AuditService(repository, clock);
        ArgumentCaptor<AuditEventEntity> captor = ArgumentCaptor.forClass(AuditEventEntity.class);

        service.append("acc-1", "ACCOUNT_LOGIN", "SESSION", "sess-1", "password=secret123 code=654321 kept");

        verify(repository).save(captor.capture());
        AuditEventEntity saved = captor.getValue();
        assertEquals("acc-1", saved.getActorId());
        assertEquals("ACCOUNT_LOGIN", saved.getAction());
        assertEquals("SESSION", saved.getObjectType());
        assertEquals("sess-1", saved.getObjectId());
        assertEquals(Instant.parse("2026-08-19T00:00:00Z"), saved.getCreatedAt());
        assertFalse(saved.getSummary().contains("secret123"));
        assertFalse(saved.getSummary().contains("654321"));
        assertTrue(saved.getSummary().contains("kept"));
        assertTrue(saved.getSummary().contains("password=***"));
    }
}
