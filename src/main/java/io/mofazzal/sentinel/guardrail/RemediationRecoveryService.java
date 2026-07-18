package io.mofazzal.sentinel.guardrail;

import io.mofazzal.sentinel.incident.application.IncidentRemediationStateService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class RemediationRecoveryService {

    private final RemediationRequestStore requests;
    private final RemediationDecisionCoordinator coordinator;
    private final IncidentRemediationStateService incidentStates;

    public RemediationRecoveryService(RemediationRequestStore requests,
                                      RemediationDecisionCoordinator coordinator,
                                      IncidentRemediationStateService incidentStates) {
        this.requests = requests;
        this.coordinator = coordinator;
        this.incidentStates = incidentStates;
    }

    @Scheduled(
            fixedDelayString = "${sentinel.remediation.recovery-scan-delay:1m}",
            initialDelayString = "${sentinel.remediation.recovery-scan-delay:1m}")
    public void recover() {
        for (UUID incidentId : requests.findPendingDecisions()) {
            try {
                coordinator.processProposal(incidentId);
            } catch (RuntimeException concurrentOrUnavailable) {
                // A later scan retries only if the durable state is still pending.
            }
        }
        for (UUID incidentId : requests.findStaleExecutions()) {
            escalateUncertainExecution(incidentId);
        }
    }

    private void escalateUncertainExecution(UUID incidentId) {
        boolean transitioned = requests.transition(incidentId,
                List.of(RemediationRequestStatus.EXECUTING), RemediationRequestStatus.ESCALATED,
                null, "SYSTEM_RECOVERY",
                "Execution outcome is uncertain; action claim remains blocked for manual reconciliation");
        if (transitioned) {
            incidentStates.escalate(incidentId);
        }
    }
}
