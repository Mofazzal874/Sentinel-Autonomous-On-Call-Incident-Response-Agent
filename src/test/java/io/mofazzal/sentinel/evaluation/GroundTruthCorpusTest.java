package io.mofazzal.sentinel.evaluation;

import org.junit.jupiter.api.Test;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GroundTruthCorpusTest {

    @Test
    void corpusHasUniqueBalancedSplitsAndCoherentOutcomes() throws IOException {
        List<EvaluationScenario> scenarios = loadCorpus();

        assertThat(scenarios).hasSize(12);
        assertThat(scenarios.stream().map(EvaluationScenario::id)).doesNotHaveDuplicates();
        assertThat(scenarios.stream().map(EvaluationScenario::split).collect(java.util.stream.Collectors.toSet()))
                .isEqualTo(EnumSet.allOf(EvaluationScenario.Split.class));
        for (EvaluationScenario.Split split : EvaluationScenario.Split.values()) {
            assertThat(scenarios).filteredOn(scenario -> scenario.split() == split).hasSize(4);
        }
        assertThat(scenarios).allSatisfy(scenario -> {
            assertThat(scenario.id()).isNotBlank();
            assertThat(scenario.service()).isNotBlank();
            assertThat(scenario.summary()).isNotBlank();
            assertThat(new HashSet<>(scenario.requiredSignals())).hasSameSizeAs(scenario.requiredSignals());
            assertThat(scenario.requiredSignals()).isNotEmpty();
            if (scenario.expectedEscalation()) {
                assertThat(scenario.expectedRunbookTitle()).isNull();
                assertThat(scenario.expectedAction()).isNull();
            } else {
                assertThat(scenario.expectedRunbookTitle()).isNotBlank();
                assertThat(scenario.expectedAction()).isNotNull();
            }
        });
    }

    static List<EvaluationScenario> loadCorpus() throws IOException {
        try (var input = GroundTruthCorpusTest.class.getResourceAsStream(
                "/evaluation/incident-ground-truth.json")) {
            if (input == null) {
                throw new IllegalStateException("Ground-truth corpus is missing");
            }
            return JsonMapper.builder().build().readValue(input, new TypeReference<>() { });
        }
    }
}
