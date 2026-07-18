package io.mofazzal.sentinel.guardrail;

import io.mofazzal.sentinel.execution.RemediationExecutor;
import io.mofazzal.sentinel.incident.application.IncidentRemediationStateService;
import io.mofazzal.sentinel.ledger.ActionDecisionRecorder;
import io.mofazzal.sentinel.ledger.ActionExecutionRequest;
import org.springframework.stereotype.Service;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;
import java.util.UUID;

@Service
public class RemediationDecisionCoordinator {

    private final RemediationRequestStore requests;
    private final GuardrailGate gate;
    private final ActionDecisionRecorder decisions;
    private final RemediationExecutor executor;
    private final IncidentRemediationStateService incidentStates;

    public RemediationDecisionCoordinator(RemediationRequestStore requests,
                                          GuardrailGate gate,
                                          ActionDecisionRecorder decisions,
                                          RemediationExecutor executor,
                                          IncidentRemediationStateService incidentStates) {
        this.requests = requests;
        this.gate = gate;
        this.decisions = decisions;
        this.executor = executor;
        this.incidentStates = incidentStates;
    }

    public GateDecision processProposal(UUID incidentId) {
        return process(incidentId, false, "AGENT", null);
    }

    @PreAuthorize("hasRole('SRE_APPROVER')")
    public GateDecision processApproved(UUID incidentId, String approver, String note) {
        return process(incidentId, true, approver, note);
    }

    private GateDecision process(UUID incidentId, boolean humanApproved, String actor, String approvalNote) {
        RemediationRequestSnapshot snapshot = requireRequest(incidentId);
        RemediationRequestStatus expected = humanApproved
                ? RemediationRequestStatus.AWAITING_APPROVAL
                : RemediationRequestStatus.PENDING_DECISION;
        if (snapshot.status() != expected) {
            throw new IllegalStateException("Remediation request is not in expected state " + expected);
        }

        RiskFacts facts = new RiskFacts(snapshot.actionType(), snapshot.serviceTier(),
                snapshot.affectedDependents(), snapshot.groundingSimilarity(), snapshot.peakTrafficWindow());
        GateRequest gateRequest = new GateRequest(snapshot.incidentId(), snapshot.serviceId(),
                snapshot.fingerprint(), facts, humanApproved);
        GateEvaluation evaluation = gate.evaluate(gateRequest);
        GateDecision decision = evaluation.decision();
        ActionExecutionRequest executionRequest = executionRequest(snapshot, decision, actor);
        if (humanApproved) {
            decisions.approved(executionRequest, approvalNote);
        }
        decisions.record(executionRequest);

        return switch (decision.type()) {
            case REQUIRE_APPROVAL -> awaitingApproval(snapshot, decision);
            case AUTO_EXECUTE, APPROVED_EXECUTE -> execute(snapshot, decision, executionRequest, expected,
                    evaluation.executionAuthorization().orElseThrow());
            case SKIP -> skipped(snapshot, decision, actor, expected);
            case DRY_RUN -> terminal(snapshot, decision, actor, expected,
                    RemediationRequestStatus.DRY_RUN);
            case REFUSE -> terminal(snapshot, decision, actor, expected,
                    RemediationRequestStatus.REFUSED);
            case ESCALATE -> terminal(snapshot, decision, actor, expected,
                    RemediationRequestStatus.ESCALATED);
        };
    }

    private GateDecision awaitingApproval(RemediationRequestSnapshot snapshot, GateDecision decision) {
        requireTransition(requests.transition(snapshot.incidentId(),
                List.of(RemediationRequestStatus.PENDING_DECISION),
                RemediationRequestStatus.AWAITING_APPROVAL, decision.risk(), null, decision.reason()));
        return decision;
    }

    private GateDecision execute(RemediationRequestSnapshot snapshot,
                                 GateDecision decision,
                                 ActionExecutionRequest executionRequest,
                                 RemediationRequestStatus expected,
                                 ExecutionAuthorization authorization) {
        if (!requests.transition(snapshot.incidentId(), List.of(expected),
                RemediationRequestStatus.EXECUTING, decision.risk(), executionRequest.actor(), decision.reason())) {
            throw new IllegalStateException("Remediation decision was already claimed");
        }
        incidentStates.beginExecution(snapshot.incidentId());
        try {
            executor.execute(executionRequest, authorization);
            requireTransition(requests.transition(snapshot.incidentId(),
                    List.of(RemediationRequestStatus.EXECUTING), RemediationRequestStatus.COMPLETED,
                    decision.risk(), executionRequest.actor(), "Execution completed"));
            incidentStates.resolve(snapshot.incidentId());
            return decision;
        } catch (RuntimeException failure) {
            requests.transition(snapshot.incidentId(), List.of(RemediationRequestStatus.EXECUTING),
                    RemediationRequestStatus.ESCALATED, decision.risk(), executionRequest.actor(),
                    "Execution failed; inspect compensation ledger");
            incidentStates.escalate(snapshot.incidentId());
            throw failure;
        }
    }

    private GateDecision skipped(RemediationRequestSnapshot snapshot,
                                 GateDecision decision,
                                 String actor,
                                 RemediationRequestStatus expected) {
        requireTransition(requests.transition(snapshot.incidentId(), List.of(expected),
                RemediationRequestStatus.SKIPPED, decision.risk(), actor, decision.reason()));
        incidentStates.resolveSkipped(snapshot.incidentId());
        return decision;
    }

    private GateDecision terminal(RemediationRequestSnapshot snapshot,
                                  GateDecision decision,
                                  String actor,
                                  RemediationRequestStatus expected,
                                  RemediationRequestStatus terminal) {
        requireTransition(requests.transition(snapshot.incidentId(), List.of(expected), terminal,
                decision.risk(), actor, decision.reason()));
        incidentStates.escalate(snapshot.incidentId());
        return decision;
    }

    private RemediationRequestSnapshot requireRequest(UUID incidentId) {
        return requests.findByIncidentId(incidentId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown remediation request for " + incidentId));
    }

    private ActionExecutionRequest executionRequest(RemediationRequestSnapshot snapshot,
                                                    GateDecision decision,
                                                    String actor) {
        return new ActionExecutionRequest(snapshot.incidentId(), snapshot.serviceId(), snapshot.fingerprint(),
                snapshot.actionType(), decision, actor);
    }

    private void requireTransition(boolean transitioned) {
        if (!transitioned) {
            throw new IllegalStateException("Concurrent remediation decision changed the request");
        }
    }
}
