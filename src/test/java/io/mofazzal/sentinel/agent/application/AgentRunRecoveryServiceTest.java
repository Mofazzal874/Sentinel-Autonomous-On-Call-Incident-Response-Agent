package io.mofazzal.sentinel.agent.application;

import io.mofazzal.sentinel.agent.config.AgentProperties;
import io.mofazzal.sentinel.agent.persistence.AgentRun;
import io.mofazzal.sentinel.agent.persistence.AgentRunRepository;
import io.mofazzal.sentinel.agent.persistence.AgentRunStatus;
import io.mofazzal.sentinel.incident.domain.Incident;
import io.mofazzal.sentinel.incident.domain.IncidentStatus;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentRunRecoveryServiceTest {

    @Test
    void staleUnknownWorkFailsClosedAndEscalatesTheIncident() {
        Instant now = Instant.parse("2026-07-19T02:00:00Z");
        AgentRunRepository runs = mock(AgentRunRepository.class);
        AgentRun run = mock(AgentRun.class);
        Incident incident = mock(Incident.class);
        when(run.getIncident()).thenReturn(incident);
        when(incident.getStatus()).thenReturn(IncidentStatus.TRIAGING);
        when(runs.findStaleForUpdate(now.minus(Duration.ofMinutes(10)), PageRequest.of(0, 20)))
                .thenReturn(List.of(run));
        AgentRunRecoveryService recovery = new AgentRunRecoveryService(runs,
                new AgentProperties(3, 12, Duration.ofHours(1), Duration.ofMinutes(10)),
                Clock.fixed(now, ZoneOffset.UTC));

        int recovered = recovery.recoverStaleRuns();

        assertThat(recovered).isOne();
        verify(run).complete(AgentRunStatus.FAILED,
                "Agent process stopped before recording a durable outcome; manual review required",
                0, now);
        verify(incident).transitionTo(IncidentStatus.ESCALATED, now);
    }
}
