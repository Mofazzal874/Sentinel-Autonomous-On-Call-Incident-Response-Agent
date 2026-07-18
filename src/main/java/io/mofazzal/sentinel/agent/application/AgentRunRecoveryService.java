package io.mofazzal.sentinel.agent.application;

import io.mofazzal.sentinel.agent.config.AgentProperties;
import io.mofazzal.sentinel.agent.persistence.AgentRunRepository;
import io.mofazzal.sentinel.agent.persistence.AgentRunStatus;
import io.mofazzal.sentinel.incident.domain.IncidentStatus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
@ConditionalOnProperty(name = "sentinel.agent.enabled", havingValue = "true")
public class AgentRunRecoveryService {

    private static final int RECOVERY_BATCH_SIZE = 20;
    private final AgentRunRepository runs;
    private final AgentProperties properties;
    private final Clock clock;

    public AgentRunRecoveryService(AgentRunRepository runs, AgentProperties properties, Clock clock) {
        this.runs = runs;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(
            fixedDelayString = "${sentinel.agent.run-recovery-scan-delay:1m}",
            initialDelayString = "${sentinel.agent.run-recovery-scan-delay:1m}")
    @Transactional
    public int recoverStaleRuns() {
        var now = clock.instant();
        var stale = runs.findStaleForUpdate(
                now.minus(properties.runRecoveryTimeout()), PageRequest.of(0, RECOVERY_BATCH_SIZE));
        for (var run : stale) {
            run.complete(AgentRunStatus.FAILED,
                    "Agent process stopped before recording a durable outcome; manual review required",
                    0, now);
            if (run.getIncident().getStatus() == IncidentStatus.TRIAGING) {
                run.getIncident().transitionTo(IncidentStatus.ESCALATED, now);
            }
        }
        return stale.size();
    }
}
