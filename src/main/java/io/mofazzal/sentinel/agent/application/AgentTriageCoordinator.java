package io.mofazzal.sentinel.agent.application;

import io.mofazzal.sentinel.agent.domain.TriageOutcome;
import io.mofazzal.sentinel.agent.domain.TriageRequest;
import io.mofazzal.sentinel.guardrail.RemediationDecisionCoordinator;
import io.mofazzal.sentinel.observability.SentinelMetrics;
import java.time.Duration;
import java.util.UUID;

public class AgentTriageCoordinator {

    private final TriageWorkflow workflow;
    private final AgentRunLifecycleService lifecycle;
    private final RemediationDecisionCoordinator remediationDecisions;
    private final SentinelMetrics metrics;

    public AgentTriageCoordinator(TriageWorkflow workflow,
                                  AgentRunLifecycleService lifecycle,
                                  RemediationDecisionCoordinator remediationDecisions,
                                  SentinelMetrics metrics) {
        this.workflow = workflow;
        this.lifecycle = lifecycle;
        this.remediationDecisions = remediationDecisions;
        this.metrics = metrics;
    }

    public TriageOutcome triage(TriageRequest request) {
        UUID runId = lifecycle.begin(request.incidentId());
        long startedAt = System.nanoTime();
        TriageOutcome outcome = null;
        try {
            outcome = workflow.triage(request);
        } catch (RuntimeException failure) {
            lifecycle.fail(runId, boundedFailureReason(failure));
            metrics.recordTriage(null, Duration.ofNanos(System.nanoTime() - startedAt));
            throw failure;
        }
        try {
            lifecycle.complete(runId, outcome);
            if (outcome.decision() == TriageOutcome.Decision.PROPOSED) {
                remediationDecisions.processProposal(request.incidentId());
            }
            return outcome;
        } finally {
            metrics.recordTriage(outcome, Duration.ofNanos(System.nanoTime() - startedAt));
        }
    }

    private static String boundedFailureReason(RuntimeException failure) {
        String message = failure.getMessage();
        if (message == null || message.isBlank()) {
            return failure.getClass().getSimpleName();
        }
        return message.substring(0, Math.min(message.length(), 500));
    }
}
