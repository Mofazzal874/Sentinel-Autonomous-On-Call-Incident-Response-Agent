package io.mofazzal.sentinel.agent.application;

import io.mofazzal.sentinel.agent.domain.TriageRequest;
import io.mofazzal.sentinel.alert.messaging.TriageCommand;
import io.mofazzal.sentinel.incident.domain.IncidentStatus;
import io.mofazzal.sentinel.incident.repository.IncidentRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class IncidentAgentDispatcher {

    private final ObjectProvider<AgentTriageCoordinator> coordinators;
    private final IncidentRepository incidents;

    public IncidentAgentDispatcher(ObjectProvider<AgentTriageCoordinator> coordinators,
                                   IncidentRepository incidents) {
        this.coordinators = coordinators;
        this.incidents = incidents;
    }

    public void dispatchIfEnabled(TriageCommand command) {
        AgentTriageCoordinator coordinator = coordinators.getIfAvailable();
        if (coordinator == null) {
            return;
        }
        var incident = incidents.findByFingerprint(command.fingerprint())
                .orElseThrow(() -> new IllegalStateException(
                        "Durable incident is missing after creation for " + command.fingerprint()));
        if (incident.getStatus() == IncidentStatus.TRIAGING) {
            throw new AgentDispatchInProgressException(
                    "Incident already has triage in progress: " + incident.getId());
        }
        if (incident.getStatus() != IncidentStatus.OPEN) {
            return;
        }
        coordinator.triage(new TriageRequest(
                incident.getId(),
                command.payload().service().trim().toLowerCase(Locale.ROOT),
                command.payload().summary(),
                command.payload().firedAt()));
    }
}
