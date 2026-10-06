package com.jobproof.modules.airesume.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobproof.modules.airesume.application.AiResumeSseService.Event;
import com.jobproof.shared.id.Ids;
import java.time.Instant;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiResumeConversationEventService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public AiResumeConversationEventService(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Transactional
    public Event append(String accountId, String conversationId, String type, Map<String, Object> payload,
            Instant now) {
        Long current = jdbc.queryForObject(
                "SELECT last_sequence FROM ai_resume_conversations WHERE id=? AND account_id=? FOR UPDATE",
                Long.class, conversationId, accountId);
        long sequence = (current == null ? 0 : current) + 1;
        jdbc.update("UPDATE ai_resume_conversations SET last_sequence=?,updated_at=? WHERE id=? AND account_id=?",
                sequence, now, conversationId, accountId);
        try {
            jdbc.update("INSERT INTO ai_resume_stream_events(id,account_id,conversation_id,sequence_no,event_type,payload_json,created_at,expires_at) VALUES(?,?,?,?,?,?,?,?)",
                    Ids.newId(), accountId, conversationId, sequence, type, mapper.writeValueAsString(payload),
                    now, now.plusSeconds(86400));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(exception);
        }
        return new Event(conversationId, sequence, type, payload, now);
    }
}
