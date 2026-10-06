package com.jobproof.modules.jobmatch.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobproof.shared.error.AppException;
import com.jobproof.shared.id.Ids;
import com.jobproof.shared.time.ClockPort;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class JobMatchSseService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final ClockPort clock;
    private final Map<String, CopyOnWriteArrayList<SseEmitter>> subscribers = new ConcurrentHashMap<>();

    public JobMatchSseService(JdbcTemplate jdbc, ObjectMapper mapper, ClockPort clock) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.clock = clock;
    }

    public SseEmitter subscribe(String accountId, String matchId, long afterSequence) {
        Integer owned = jdbc.queryForObject("SELECT COUNT(*) FROM job_match_tasks WHERE id=? AND account_id=?",
                Integer.class, matchId, accountId);
        if (owned == null || owned == 0) throw AppException.forbidden("OBJECT_FORBIDDEN", "不能订阅他人的岗位匹配任务");
        SseEmitter emitter = new SseEmitter(0L);
        var list = subscribers.computeIfAbsent(matchId, ignored -> new CopyOnWriteArrayList<>());
        list.add(emitter);
        Runnable cleanup = () -> { list.remove(emitter); if (list.isEmpty()) subscribers.remove(matchId, list); };
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(error -> cleanup.run());
        try {
            for (Event event : replay(accountId, matchId, afterSequence)) send(emitter, event);
            emitter.send(SseEmitter.event().name("heartbeat").data(Map.of("at", clock.now().toString())));
        } catch (IOException | IllegalStateException exception) {
            cleanup.run();
            emitter.complete();
        }
        return emitter;
    }

    public Event append(String accountId, String matchId, String type, Map<String, Object> payload) {
        Long current = jdbc.queryForObject("SELECT COALESCE(MAX(sequence_no),0) FROM job_match_stream_events WHERE match_id=?",
                Long.class, matchId);
        long sequence = (current == null ? 0 : current) + 1;
        Instant now = clock.now();
        jdbc.update("INSERT INTO job_match_stream_events(id,match_id,account_id,sequence_no,event_type,payload_json,created_at,expires_at) VALUES(?,?,?,?,?,?,?,?)",
                Ids.newId(), matchId, accountId, sequence, type, json(payload), now, now.plusSeconds(7 * 86400L));
        Event event = new Event(matchId, sequence, type, payload, now);
        publish(event);
        return event;
    }

    public List<Event> replay(String accountId, String matchId, long afterSequence) {
        return jdbc.query("SELECT match_id,sequence_no,event_type,payload_json,created_at FROM job_match_stream_events WHERE account_id=? AND match_id=? AND sequence_no>? AND expires_at>? ORDER BY sequence_no",
                (rs, n) -> new Event(rs.getString("match_id"), rs.getLong("sequence_no"),
                        rs.getString("event_type"), read(rs.getString("payload_json")),
                        rs.getTimestamp("created_at").toInstant()),
                accountId, matchId, Math.max(0, afterSequence), clock.now());
    }

    @Scheduled(fixedDelayString = "${jobproof.job-match.sse-heartbeat-ms:15000}")
    void heartbeat() {
        subscribers.forEach((matchId, list) -> {
            for (SseEmitter emitter : list) {
                try { emitter.send(SseEmitter.event().name("heartbeat").data(Map.of("at", clock.now().toString()))); }
                catch (IOException | IllegalStateException exception) { list.remove(emitter); emitter.complete(); }
            }
            if (list.isEmpty()) subscribers.remove(matchId, list);
        });
    }

    private void publish(Event event) {
        var list = subscribers.get(event.matchId());
        if (list == null) return;
        for (SseEmitter emitter : list) {
            try { send(emitter, event); }
            catch (IOException | IllegalStateException exception) { list.remove(emitter); emitter.complete(); }
        }
    }
    private void send(SseEmitter emitter, Event event) throws IOException { emitter.send(SseEmitter.event().id(String.valueOf(event.sequence())).name(event.type()).data(event.payload())); }
    private String json(Object value) { try { return mapper.writeValueAsString(value); } catch (Exception e) { throw new IllegalStateException(e); } }
    private Map<String, Object> read(String value) { try { return mapper.readValue(value, new TypeReference<>() {}); } catch (Exception e) { return Map.of("unavailable", true); } }
    public record Event(String matchId, long sequence, String type, Map<String, Object> payload, Instant createdAt) {}
}
