package io.mofazzal.sentinel.agent.application;

import io.mofazzal.sentinel.agent.domain.TriageOutcome;
import io.mofazzal.sentinel.agent.domain.TriageRequest;
import java.util.UUID;

public class AgentTriageCoordinator {

    private final TriageWorkflow workflow;
    private final AgentRunLifecycleService lifecycle;

    public AgentTriageCoordinator(TriageWorkflow workflow, AgentRunLifecycleService lifecycle) {
        this.workflow = workflow;
        this.lifecycle = lifecycle;
    }

    public TriageOutcome triage(TriageRequest request) {
        UUID runId = lifecycle.begin(request.incidentId());
        try {
            TriageOutcome outcome = workflow.triage(request);
            lifecycle.complete(runId, outcome);
            return outcome;
        } catch (RuntimeException failure) {
            lifecycle.fail(runId, boundedFailureReason(failure));
            throw failure;
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
