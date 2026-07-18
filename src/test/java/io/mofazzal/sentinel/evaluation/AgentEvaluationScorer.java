package io.mofazzal.sentinel.evaluation;

import java.util.List;

final class AgentEvaluationScorer {

    EvaluationScore score(List<EvaluationScenario> truth, List<EvaluationPrediction> predictions) {
        if (truth.size() != predictions.size() || truth.isEmpty()) {
            throw new IllegalArgumentException("Ground truth and predictions must have the same non-zero size");
        }
        int classificationHits = 0;
        int signalCoverageHits = 0;
        int retrievalEligible = 0;
        int retrievalHits = 0;
        int outcomeHits = 0;
        int hallucinations = 0;
        for (int index = 0; index < truth.size(); index++) {
            EvaluationScenario expected = truth.get(index);
            EvaluationPrediction actual = predictions.get(index);
            classificationHits += expected.expectedType() == actual.type() ? 1 : 0;
            signalCoverageHits += actual.signals().containsAll(expected.requiredSignals()) ? 1 : 0;
            if (expected.expectedRunbookTitle() != null) {
                retrievalEligible++;
                retrievalHits += actual.retrievedRunbookTitles().contains(expected.expectedRunbookTitle()) ? 1 : 0;
            }
            if (actual.proposedRunbookTitle() != null
                    && !actual.retrievedRunbookTitles().contains(actual.proposedRunbookTitle())) {
                hallucinations++;
            }
            boolean outcomeCorrect = expected.expectedEscalation()
                    ? actual.escalated() && actual.proposedRunbookTitle() == null
                    : !actual.escalated()
                    && expected.expectedRunbookTitle().equals(actual.proposedRunbookTitle())
                    && expected.expectedAction() == actual.proposedAction();
            outcomeHits += outcomeCorrect ? 1 : 0;
        }
        return new EvaluationScore(truth.size(), classificationHits, signalCoverageHits,
                retrievalEligible, retrievalHits, outcomeHits, hallucinations);
    }

    record EvaluationScore(
            int scenarios,
            int classificationHits,
            int signalCoverageHits,
            int retrievalEligible,
            int retrievalHits,
            int outcomeHits,
            int hallucinations
    ) {
        double classificationAccuracy() {
            return ratio(classificationHits, scenarios);
        }

        double requiredSignalCoverage() {
            return ratio(signalCoverageHits, scenarios);
        }

        double retrievalRecall() {
            return ratio(retrievalHits, retrievalEligible);
        }

        double outcomeAccuracy() {
            return ratio(outcomeHits, scenarios);
        }

        private static double ratio(int numerator, int denominator) {
            return denominator == 0 ? 1.0 : (double) numerator / denominator;
        }
    }
}
