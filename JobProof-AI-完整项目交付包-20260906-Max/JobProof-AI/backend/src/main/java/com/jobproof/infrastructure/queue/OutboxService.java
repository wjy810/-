package com.jobproof.infrastructure.queue;

import com.jobproof.infrastructure.persistence.OutboxEventEntity;
import com.jobproof.infrastructure.persistence.OutboxEventJpaRepository;
import com.jobproof.shared.id.Ids;
import com.jobproof.shared.time.ClockPort;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OutboxService {

    private final OutboxEventJpaRepository repository;
    private final ClockPort clock;
    private final ObjectMapper objectMapper;

    public OutboxService(OutboxEventJpaRepository repository, ClockPort clock, ObjectMapper objectMapper) {
        this.repository = repository;
        this.clock = clock;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void enqueue(String eventType, Map<String, Object> payload) {
        OutboxEventEntity event = new OutboxEventEntity();
        event.setId(Ids.newId());
        event.setEventType(eventType);
        event.setPayloadJson(write(payload));
        event.setCreatedAt(clock.now());
        repository.save(event);
    }

    private String write(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
