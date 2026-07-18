package io.mofazzal.sentinel.guardrail;

import io.mofazzal.sentinel.incident.application.IncidentRemediationStateService;
import io.mofazzal.sentinel.ledger.ActionDecisionRecorder;
import io.mofazzal.sentinel.ledger.ActionExecutionRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

@Service
public class RemediationApprovalService {

    private final RemediationRequestStore requests;
    private final RemediationDecisionCoordinator coordinator;
    private final DeterministicRiskScorer riskScorer;
    private final ActionDecisionRecorder decisions;
    private final IncidentRemediationStateService incidentStates;
    private final Clock clock;

    public RemediationApprovalService(RemediationRequestStore requests,
                                      RemediationDecisionCoordinator coordinator,
                                      DeterministicRiskScorer riskScorer,
                                      ActionDecisionRecorder decisions,
                                      IncidentRemediationStateService incidentStates,
                                      Clock clock) {
        this.requests = requests;
        this.coordinator = coordinator;
        this.riskScorer = riskScorer;
        this.decisions = decisions;
        this.incidentStates = incidentStates;
        this.clock = clock;
    }

    @PreAuthorize("hasRole('SRE_APPROVER')")
    public ApprovalResponse decide(UUID incidentId, ApprovalRequest request, String approver) {
        RemediationRequestSnapshot snapshot = requireAwaiting(incidentId);
        if (!snapshot.approvalExpiresAt().isAfter(clock.instant())) {
            reject(snapshot, "SYSTEM_TIMEOUT", "Approval window expired");
            throw new IllegalStateException("Approval window has expired");
        }
        if (request.decision() == ApprovalDecision.APPROVE) {
            GateDecision gateDecision = coordinator.processApproved(incidentId, approver, request.note());
            return new ApprovalResponse(incidentId, gateDecision.type().name());
        }
        reject(snapshot, approver, request.note());
        return new ApprovalResponse(incidentId, RemediationRequestStatus.REJECTED.name());
    }

    @Scheduled(
            fixedDelayString = "${sentinel.remediation.approval-scan-delay:1m}",
            initialDelayString = "${sentinel.remediation.approval-scan-delay:1m}")
    public void escalateExpiredApprovals() {
        for (UUID incidentId : requests.findExpiredAwaitingApproval()) {
            requests.findByIncidentId(incidentId)
                    .filter(snapshot -> snapshot.status() == RemediationRequestStatus.AWAITING_APPROVAL)
                    .ifPresent(snapshot -> reject(snapshot, "SYSTEM_TIMEOUT", "Approval window expired"));
        }
    }

    private void reject(RemediationRequestSnapshot snapshot, String actor, String note) {
        RiskBreakdown risk = riskScorer.score(facts(snapshot));
        GateDecision rejection = new GateDecision(GateDecisionType.REQUIRE_APPROVAL,
                "Human approval was rejected or expired", risk);
        ActionExecutionRequest executionRequest = new ActionExecutionRequest(
                snapshot.incidentId(), snapshot.serviceId(), snapshot.fingerprint(),
                snapshot.actionType(), rejection, actor);
        decisions.rejected(executionRequest, note);
        if (!requests.transition(snapshot.incidentId(), List.of(RemediationRequestStatus.AWAITING_APPROVAL),
                RemediationRequestStatus.REJECTED, risk, actor, note)) {
            throw new IllegalStateException("Approval was already decided");
        }
        incidentStates.escalate(snapshot.incidentId());
    }

    private RemediationRequestSnapshot requireAwaiting(UUID incidentId) {
        RemediationRequestSnapshot snapshot = requests.findByIncidentId(incidentId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown remediation request for " + incidentId));
        if (snapshot.status() != RemediationRequestStatus.AWAITING_APPROVAL) {
            throw new IllegalStateException("Remediation request is not awaiting approval");
        }
        return snapshot;
    }

    private RiskFacts facts(RemediationRequestSnapshot snapshot) {
        return new RiskFacts(snapshot.actionType(), snapshot.serviceTier(), snapshot.affectedDependents(),
                snapshot.groundingSimilarity(), snapshot.peakTrafficWindow());
    }
}
