package io.mofazzal.sentinel.agent.application;

import io.mofazzal.sentinel.agent.domain.TriageOutcome;
import io.mofazzal.sentinel.agent.domain.TriageRequest;
import io.mofazzal.sentinel.guardrail.RemediationDecisionCoordinator;
import java.util.UUID;

public class AgentTriageCoordinator {

    private final TriageWorkflow workflow;
    private final AgentRunLifecycleService lifecycle;
    private final RemediationDecisionCoordinator remediationDecisions;

    public AgentTriageCoordinator(TriageWorkflow workflow,
                                  AgentRunLifecycleService lifecycle,
                                  RemediationDecisionCoordinator remediationDecisions) {
        this.workflow = workflow;
        this.lifecycle = lifecycle;
        this.remediationDecisions = remediationDecisions;
    }

    public TriageOutcome triage(TriageRequest request) {
        UUID runId = lifecycle.begin(request.incidentId());
        TriageOutcome outcome;
        try {
            outcome = workflow.triage(request);
        } catch (RuntimeException failure) {
            lifecycle.fail(runId, boundedFailureReason(failure));
            throw failure;
        }
        lifecycle.complete(runId, outcome);
        if (outcome.decision() == TriageOutcome.Decision.PROPOSED) {
            remediationDecisions.processProposal(request.incidentId());
        }
        return outcome;
    }

    private static String boundedFailureReason(RuntimeException failure) {
        String message = failure.getMessage();
        if (message == null || message.isBlank()) {
            return failure.getClass().getSimpleName();
        }
        return message.substring(0, Math.min(message.length(), 500));
    }
}
