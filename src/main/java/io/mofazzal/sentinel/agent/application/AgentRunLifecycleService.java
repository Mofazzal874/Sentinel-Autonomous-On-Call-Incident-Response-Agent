package io.mofazzal.sentinel.agent.application;

import io.mofazzal.sentinel.agent.domain.TriageOutcome;
import io.mofazzal.sentinel.agent.persistence.AgentRun;
import io.mofazzal.sentinel.agent.persistence.AgentRunRepository;
import io.mofazzal.sentinel.agent.persistence.AgentRunStatus;
import io.mofazzal.sentinel.incident.domain.Incident;
import io.mofazzal.sentinel.incident.domain.IncidentStatus;
import io.mofazzal.sentinel.incident.repository.IncidentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

@Service
public class AgentRunLifecycleService {

    private final IncidentRepository incidents;
    private final AgentRunRepository runs;
    private final Clock clock;

    public AgentRunLifecycleService(IncidentRepository incidents, AgentRunRepository runs, Clock clock) {
        this.incidents = incidents;
        this.runs = runs;
        this.clock = clock;
    }

    @Transactional
    public UUID begin(UUID incidentId) {
        Incident incident = incidents.findForAgentStart(incidentId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown incident " + incidentId));
        if (incident.getStatus() != IncidentStatus.OPEN) {
            throw new IllegalStateException("Only an OPEN incident can begin triage");
        }
        if (runs.findRunningForUpdate(incidentId).isPresent()) {
            throw new IllegalStateException("Incident already has a running agent workflow");
        }
        incident.transitionTo(IncidentStatus.TRIAGING, clock.instant());
        return runs.save(new AgentRun(incident, clock.instant())).getId();
    }

    @Transactional
    public void complete(UUID runId, TriageOutcome outcome) {
        AgentRun run = runs.findForCompletion(runId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown agent run " + runId));
        Incident incident = run.getIncident();
        AgentRunStatus runStatus;
        IncidentStatus incidentStatus;
        if (outcome.decision() == TriageOutcome.Decision.PROPOSED) {
            runStatus = AgentRunStatus.PROPOSED;
            incidentStatus = IncidentStatus.AWAITING_APPROVAL;
        } else {
            runStatus = AgentRunStatus.ESCALATED;
            incidentStatus = IncidentStatus.ESCALATED;
        }
        run.complete(runStatus, outcome.reason(), outcome.attempts(), clock.instant());
        incident.transitionTo(incidentStatus, clock.instant());
    }

    @Transactional
    public void fail(UUID runId, String reason) {
        AgentRun run = runs.findForCompletion(runId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown agent run " + runId));
        run.complete(AgentRunStatus.FAILED, reason, 0, clock.instant());
        run.getIncident().transitionTo(IncidentStatus.ESCALATED, clock.instant());
    }
}
