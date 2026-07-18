package io.mofazzal.sentinel.incident.application;

import io.mofazzal.sentinel.incident.domain.Incident;
import io.mofazzal.sentinel.incident.domain.IncidentStatus;
import io.mofazzal.sentinel.incident.repository.IncidentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

@Service
public class IncidentRemediationStateService {

    private final IncidentRepository incidents;
    private final Clock clock;

    public IncidentRemediationStateService(IncidentRepository incidents, Clock clock) {
        this.incidents = incidents;
        this.clock = clock;
    }

    @Transactional
    public void beginExecution(UUID incidentId) {
        Incident incident = locked(incidentId);
        if (incident.getStatus() != IncidentStatus.AWAITING_APPROVAL) {
            throw new IllegalStateException("Incident is not awaiting a remediation decision");
        }
        incident.transitionTo(IncidentStatus.REMEDIATING, clock.instant());
    }

    @Transactional
    public void resolve(UUID incidentId) {
        Incident incident = locked(incidentId);
        if (incident.getStatus() != IncidentStatus.REMEDIATING) {
            throw new IllegalStateException("Incident is not remediating");
        }
        incident.transitionTo(IncidentStatus.RESOLVED, clock.instant());
    }

    @Transactional
    public void resolveSkipped(UUID incidentId) {
        Incident incident = locked(incidentId);
        if (incident.getStatus() == IncidentStatus.RESOLVED) {
            return;
        }
        if (incident.getStatus() != IncidentStatus.AWAITING_APPROVAL) {
            throw new IllegalStateException("Incident is not awaiting a remediation decision");
        }
        incident.transitionTo(IncidentStatus.REMEDIATING, clock.instant());
        incident.transitionTo(IncidentStatus.RESOLVED, clock.instant());
    }

    @Transactional
    public void escalate(UUID incidentId) {
        Incident incident = locked(incidentId);
        if (incident.getStatus() == IncidentStatus.ESCALATED) {
            return;
        }
        if (incident.getStatus() != IncidentStatus.AWAITING_APPROVAL
                && incident.getStatus() != IncidentStatus.REMEDIATING) {
            throw new IllegalStateException("Incident cannot be escalated from " + incident.getStatus());
        }
        incident.transitionTo(IncidentStatus.ESCALATED, clock.instant());
    }

    private Incident locked(UUID incidentId) {
        return incidents.findForAgentStart(incidentId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown incident " + incidentId));
    }
}
