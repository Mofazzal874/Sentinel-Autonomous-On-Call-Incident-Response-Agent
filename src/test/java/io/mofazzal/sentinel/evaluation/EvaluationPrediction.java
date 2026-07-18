package io.mofazzal.sentinel.evaluation;

import io.mofazzal.sentinel.agent.domain.EvidenceSignal;
import io.mofazzal.sentinel.agent.domain.IncidentType;
import io.mofazzal.sentinel.fleet.domain.RemediationActionType;

import java.util.List;

record EvaluationPrediction(
        IncidentType type,
        List<EvidenceSignal> signals,
        List<String> retrievedRunbookTitles,
        String proposedRunbookTitle,
        RemediationActionType proposedAction,
        boolean escalated
) {
}
