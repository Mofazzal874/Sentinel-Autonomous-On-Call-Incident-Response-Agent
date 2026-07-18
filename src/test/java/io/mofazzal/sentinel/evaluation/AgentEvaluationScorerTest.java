package io.mofazzal.sentinel.evaluation;

import io.mofazzal.sentinel.agent.domain.EvidenceSignal;
import io.mofazzal.sentinel.agent.domain.IncidentType;
import io.mofazzal.sentinel.fleet.domain.RemediationActionType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AgentEvaluationScorerTest {

    @Test
    void scoresEachQualityDimensionWithoutHidingHallucinations() {
        EvaluationScenario grounded = new EvaluationScenario("one", EvaluationScenario.Split.DEVELOPMENT,
                "payments-api", "Errors after deploy", IncidentType.BAD_DEPLOY,
                List.of(EvidenceSignal.DEPLOYMENTS, EvidenceSignal.METRICS, EvidenceSignal.RUNBOOKS),
                "Rollback", RemediationActionType.ROLLBACK_DEPLOYMENT, false);
        EvaluationScenario unknown = new EvaluationScenario("two", EvaluationScenario.Split.DEVELOPMENT,
                "catalog-api", "Vague symptom", IncidentType.UNKNOWN,
                List.of(EvidenceSignal.LOGS, EvidenceSignal.RUNBOOKS), null, null, true);
        EvaluationPrediction correct = new EvaluationPrediction(IncidentType.BAD_DEPLOY,
                grounded.requiredSignals(), List.of("Rollback"), "Rollback",
                RemediationActionType.ROLLBACK_DEPLOYMENT, false);
        EvaluationPrediction hallucinated = new EvaluationPrediction(IncidentType.DEPENDENCY_OUTAGE,
                List.of(EvidenceSignal.LOGS), List.of(), "Invented runbook",
                RemediationActionType.RESTART_SERVICE, false);

        var score = new AgentEvaluationScorer().score(
                List.of(grounded, unknown), List.of(correct, hallucinated));

        assertThat(score.classificationAccuracy()).isEqualTo(0.5);
        assertThat(score.requiredSignalCoverage()).isEqualTo(0.5);
        assertThat(score.retrievalRecall()).isEqualTo(1.0);
        assertThat(score.outcomeAccuracy()).isEqualTo(0.5);
        assertThat(score.hallucinations()).isEqualTo(1);
    }
}
