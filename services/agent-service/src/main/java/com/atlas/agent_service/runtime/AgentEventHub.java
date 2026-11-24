package com.atlas.agent_service.runtime;

import com.atlas.agent_service.entity.AgentJob;
import com.atlas.agent_service.repository.AgentJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.json.JsonMapper;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
public class AgentEventHub {
    private final AgentJobRepository jobs;
    private final JsonMapper json = JsonMapper.builder().build();
    private final Map<UUID, Run> runs = new ConcurrentHashMap<>();
    public static class Run {
        final List<Map<String,Object>> events = new ArrayList<>();
        final List<SseEmitter> clients = new ArrayList<>();
        final Map<String,Map<String,Object>> requests = new HashMap<>();
        Consumer<Map<String,Object>> control;
        boolean cancelled, finished;
        int sequence;
        long saved, touched = System.currentTimeMillis();
    }
    @SuppressWarnings("unchecked")
    private Run run(AgentJob job) {
        return runs.computeIfAbsent(job.getId(), key -> {
            Run run = new Run();
            if (job.getEventsJson() != null) {
                List<Map<String,Object>> events = json.readValue(job.getEventsJson(), List.class);
                run.events.addAll(events);
                run.sequence = events.stream().mapToInt(event -> ((Number)event.get("sequence")).intValue()).max().orElse(0);
            }
            return run;
        });
    }
    public SseEmitter stream(AgentJob job, int after) {
        Run run = run(job);
        SseEmitter emitter = new SseEmitter(60_000L);
        synchronized (run) {
            for (var event : run.events) if (((Number) event.get("sequence")).intValue() > after) send(emitter, event);
            if (!List.of("PENDING","RUNNING").contains(job.getStatus().name()) || run.finished) emitter.complete();
            else run.clients.add(emitter);
        }
        Runnable remove = () -> { synchronized (run) { run.clients.remove(emitter); } };
        emitter.onCompletion(remove); emitter.onTimeout(remove); emitter.onError(error -> remove.run());
        return emitter;
    }
    public void attach(AgentJob job, Consumer<Map<String,Object>> control) {
        Run run = run(job);
        synchronized (run) { run.control = control; if (run.cancelled) control.accept(Map.of("type", "cancel")); }
    }
    public void publish(AgentJob job, Map<String,Object> payload) {
        Run run = run(job);
        synchronized (run) {
            var event = new LinkedHashMap<>(payload);
            event.put("sequence", ++run.sequence); event.put("time", Instant.now().toString());
            run.touched = System.currentTimeMillis();
            if (run.events.size() < 8000) run.events.add(event);
            String type = String.valueOf(event.get("type"));
            if (type.equals("approval") || type.equals("question")) run.requests.put(String.valueOf(event.get("requestId")), event);
            if (type.equals("request.resolved")) run.requests.remove(String.valueOf(event.get("requestId")));
            run.clients.removeIf(client -> !send(client, event));
            job.setEventsJson(json.writeValueAsString(run.events));
            if (System.currentTimeMillis() - run.saved > 500 || type.equals("approval") || type.equals("question")) {
                jobs.updateEvents(job.getId(), job.getEventsJson()); run.saved = System.currentTimeMillis();
            }
        }
    }
    private boolean send(SseEmitter emitter, Map<String,Object> event) {
        try { emitter.send(SseEmitter.event().id(event.get("sequence").toString()).name("agent").data(event)); return true; }
        catch (Exception error) { return false; }
    }
    public void answer(AgentJob job, String requestId, boolean approved, Map<String,Object> answers) {
        Run run = run(job);
        synchronized (run) {
            var request = run.requests.remove(requestId);
            if (run.control == null || run.finished || request == null)
                throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT, "This request is no longer waiting for an answer.");
            run.control.accept(Map.of("type", "answer", "requestId", requestId, "approved", approved, "answers", answers == null ? Map.of() : answers));
        }
    }
    public void cancel(AgentJob job) {
        Run run = run(job);
        synchronized (run) {
            if (run.finished) return;
            run.cancelled = true;
            if (run.control != null) run.control.accept(Map.of("type", "cancel"));
        }
    }
    public boolean cancelled(AgentJob job) { Run run = run(job); synchronized (run) { return run.cancelled; } }
    public void finish(AgentJob job) {
        Run run = run(job);
        synchronized (run) {
            publish(job, Map.of("type", "turn.end", "status", job.getStatus().name()));
            run.finished = true; run.control = null; run.requests.clear();
            for (var client : List.copyOf(run.clients)) client.complete();
            run.clients.clear();
            jobs.updateEvents(job.getId(), job.getEventsJson());
        }
    }
    @Scheduled(fixedDelay = 15_000)
    public void heartbeat() {
        runs.forEach((id, run) -> { synchronized (run) {
            if (run.finished && System.currentTimeMillis() - run.touched > 3_600_000) runs.remove(id, run);
            else run.clients.removeIf(client -> { try { client.send(SseEmitter.event().comment("keepalive")); return false; } catch (Exception e) { return true; } });
        } });
    }
}
