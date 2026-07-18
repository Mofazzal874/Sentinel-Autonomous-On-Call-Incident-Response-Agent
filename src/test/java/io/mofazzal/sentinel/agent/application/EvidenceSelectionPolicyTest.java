package io.mofazzal.sentinel.agent.application;

import io.mofazzal.sentinel.agent.domain.EvidenceSignal;
import io.mofazzal.sentinel.agent.domain.IncidentType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EvidenceSelectionPolicyTest {

    @Test
    void givesEveryClassItsBoundedReadOnlyEvidenceSet() {
        assertThat(EvidenceSelectionPolicy.signalsFor(IncidentType.BAD_DEPLOY)).containsExactly(
                EvidenceSignal.DEPLOYMENTS, EvidenceSignal.METRICS, EvidenceSignal.RUNBOOKS);
        assertThat(EvidenceSelectionPolicy.signalsFor(IncidentType.RESOURCE_EXHAUSTION)).containsExactly(
                EvidenceSignal.METRICS, EvidenceSignal.LOGS, EvidenceSignal.RUNBOOKS);
        assertThat(EvidenceSelectionPolicy.signalsFor(IncidentType.DEPENDENCY_OUTAGE)).containsExactly(
                EvidenceSignal.METRICS, EvidenceSignal.LOGS, EvidenceSignal.RUNBOOKS);
        assertThat(EvidenceSelectionPolicy.signalsFor(IncidentType.UNKNOWN)).containsExactly(
                EvidenceSignal.LOGS, EvidenceSignal.RUNBOOKS);
    }
}
