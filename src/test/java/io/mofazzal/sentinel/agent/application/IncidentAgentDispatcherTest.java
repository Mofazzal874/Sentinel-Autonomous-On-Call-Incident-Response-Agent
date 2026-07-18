package io.mofazzal.sentinel.agent.application;

import io.mofazzal.sentinel.alert.api.AlertPayload;
import io.mofazzal.sentinel.alert.messaging.TriageCommand;
import io.mofazzal.sentinel.incident.domain.Incident;
import io.mofazzal.sentinel.incident.domain.IncidentSeverity;
import io.mofazzal.sentinel.incident.domain.IncidentStatus;
import io.mofazzal.sentinel.incident.repository.IncidentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class IncidentAgentDispatcherTest {

    private final ObjectProvider<AgentTriageCoordinator> coordinators = mock(ObjectProvider.class);
    private final IncidentRepository incidents = mock(IncidentRepository.class);
    private final IncidentAgentDispatcher dispatcher = new IncidentAgentDispatcher(coordinators, incidents);

    @Test
    void agentDisabledDoesNotReadOrChangeTheIncident() {
        when(coordinators.getIfAvailable()).thenReturn(null);

        dispatcher.dispatchIfEnabled(command());

        verify(incidents, never()).findByFingerprint(any());
    }

    @Test
    void openIncidentIsDispatchedWithTheDurableIdAndAlertFacts() {
        AgentTriageCoordinator coordinator = mock(AgentTriageCoordinator.class);
        Incident incident = incident(IncidentStatus.OPEN);
        when(coordinators.getIfAvailable()).thenReturn(coordinator);
        when(incidents.findByFingerprint("fingerprint")).thenReturn(Optional.of(incident));

        dispatcher.dispatchIfEnabled(command());

        verify(coordinator).triage(new io.mofazzal.sentinel.agent.domain.TriageRequest(
                incident.getId(), "payments-api", "Errors rose after deployment",
                Instant.parse("2026-07-19T00:00:00Z")));
    }

    @Test
    void terminalIncidentMakesRedeliveryAcknowledgeSafeWithoutASecondRun() {
        AgentTriageCoordinator coordinator = mock(AgentTriageCoordinator.class);
        Incident incident = incident(IncidentStatus.ESCALATED);
        when(coordinators.getIfAvailable()).thenReturn(coordinator);
        when(incidents.findByFingerprint("fingerprint")).thenReturn(Optional.of(incident));

        dispatcher.dispatchIfEnabled(command());

        verify(coordinator, never()).triage(any());
    }

    @Test
    void activeTriageIsRetriedInsteadOfSilentlyAcknowledged() {
        AgentTriageCoordinator coordinator = mock(AgentTriageCoordinator.class);
        Incident incident = incident(IncidentStatus.TRIAGING);
        when(coordinators.getIfAvailable()).thenReturn(coordinator);
        when(incidents.findByFingerprint("fingerprint"))
                .thenReturn(Optional.of(incident));

        assertThatThrownBy(() -> dispatcher.dispatchIfEnabled(command()))
                .isInstanceOf(AgentDispatchInProgressException.class);
    }

    private Incident incident(IncidentStatus status) {
        Incident incident = mock(Incident.class);
        when(incident.getId()).thenReturn(UUID.fromString("40000000-0000-0000-0000-000000000001"));
        when(incident.getStatus()).thenReturn(status);
        return incident;
    }

    private TriageCommand command() {
        Instant firedAt = Instant.parse("2026-07-19T00:00:00Z");
        AlertPayload payload = new AlertPayload(" Payments-API ", "HighErrorRate",
                IncidentSeverity.SEV2, firedAt, "Errors rose after deployment", Map.of());
        return TriageCommand.create("fingerprint", payload, firedAt.plusSeconds(10));
    }
}
