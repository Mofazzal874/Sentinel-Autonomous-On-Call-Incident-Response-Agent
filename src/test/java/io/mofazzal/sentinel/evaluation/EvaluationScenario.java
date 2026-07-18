package io.mofazzal.sentinel.evaluation;

import io.mofazzal.sentinel.agent.domain.EvidenceSignal;
import io.mofazzal.sentinel.agent.domain.IncidentType;
import io.mofazzal.sentinel.fleet.domain.RemediationActionType;

import java.util.List;

record EvaluationScenario(
        String id,
        Split split,
        String service,
        String summary,
        IncidentType expectedType,
        List<EvidenceSignal> requiredSignals,
        String expectedRunbookTitle,
        RemediationActionType expectedAction,
        boolean expectedEscalation
) {
    enum Split {
        DEVELOPMENT,
        VALIDATION,
        HOLDOUT
    }
}
