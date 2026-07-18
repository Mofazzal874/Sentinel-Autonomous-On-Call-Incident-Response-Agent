package io.mofazzal.sentinel.guardrail;

import io.mofazzal.sentinel.fleet.domain.RemediationActionType;
import io.mofazzal.sentinel.fleet.domain.ServiceTier;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeterministicRiskScorerTest {

    private final DeterministicRiskScorer scorer = new DeterministicRiskScorer();

    @Test
    void scoresAHighConfidenceStandardServiceRestartAsLowRisk() {
        RiskBreakdown risk = scorer.score(new RiskFacts(
                RemediationActionType.RESTART_SERVICE,
                ServiceTier.STANDARD,
                0,
                0.91,
                false));

        assertThat(risk).isEqualTo(new RiskBreakdown(1, 1, 0, 0, 0, 2));
    }

    @Test
    void exposesEveryComponentOfAHighRiskRollback() {
        RiskBreakdown risk = scorer.score(new RiskFacts(
                RemediationActionType.ROLLBACK_DEPLOYMENT,
                ServiceTier.CRITICAL,
                3,
                0.60,
                true));

        assertThat(risk).isEqualTo(new RiskBreakdown(4, 4, 3, 3, 2, 16));
    }

    @Test
    void treatsTheConfidenceBoundaryAsNoPenalty() {
        RiskBreakdown risk = scorer.score(new RiskFacts(
                RemediationActionType.SCALE_OUT,
                ServiceTier.STANDARD,
                1,
                0.75,
                false));

        assertThat(risk.confidencePenalty()).isZero();
        assertThat(risk.total()).isEqualTo(4);
    }

    @Test
    void capsBlastRadiusWeightToKeepTheScaleBounded() {
        RiskBreakdown risk = scorer.score(new RiskFacts(
                RemediationActionType.CLEAR_CACHE,
                ServiceTier.CRITICAL,
                50,
                1.0,
                false));

        assertThat(risk.blastRadius()).isEqualTo(10);
        assertThat(risk.total()).isEqualTo(17);
    }

    @Test
    void rejectsInvalidSafetyFacts() {
        assertThatThrownBy(() -> new RiskFacts(
                RemediationActionType.RESTART_SERVICE,
                ServiceTier.STANDARD,
                -1,
                0.8,
                false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("affectedDependents");

        assertThatThrownBy(() -> new RiskFacts(
                RemediationActionType.RESTART_SERVICE,
                ServiceTier.STANDARD,
                0,
                Double.NaN,
                false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("retrievalSimilarity");
    }

    @Test
    void rejectsAnInconsistentBreakdownTotal() {
        assertThatThrownBy(() -> new RiskBreakdown(1, 1, 0, 0, 0, 99))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("total");
    }
}
