package com.atlas.agent_service.runtime;
import com.atlas.agent_service.entity.AgentJob;
import com.atlas.agent_service.repository.AgentJobRepository;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class AgentEventHubTest {
    @Test
    void approvalsAreExplicitSingleUseAndCancellationSurvivesQueueing() {
        var hub = new AgentEventHub(mock(AgentJobRepository.class));
        var job = new AgentJob(); job.setId(UUID.randomUUID());
        var sent = new AtomicReference<Map<String,Object>>();
        hub.attach(job, sent::set);
        hub.publish(job, Map.of("type", "approval", "requestId", "r1", "text", "python3 hello.py"));
        assertNull(sent.get());
        hub.answer(job, "r1", true, null);
        assertEquals(true, sent.get().get("approved"));
        assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> hub.answer(job, "r1", true, null));
        assertTrue(job.getEventsJson().contains("python3 hello.py"));
        var queued = new AgentJob(); queued.setId(UUID.randomUUID());
        hub.cancel(queued); hub.attach(queued, sent::set);
        assertEquals("cancel", sent.get().get("type")); assertTrue(hub.cancelled(queued));
    }
}
