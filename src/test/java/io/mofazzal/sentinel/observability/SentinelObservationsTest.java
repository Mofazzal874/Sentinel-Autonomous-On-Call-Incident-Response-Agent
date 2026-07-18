package io.mofazzal.sentinel.observability;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SentinelObservationsTest {

    @Test
    void nestedStagesRetainTheCurrentParentObservation() {
        ObservationRegistry registry = ObservationRegistry.create();
        Map<String, String> parents = new LinkedHashMap<>();
        registry.observationConfig().observationHandler(new ObservationHandler<>() {
            @Override
            public void onStart(Observation.Context context) {
                var parent = context.getParentObservation();
                parents.put(context.getName(), parent == null
                        ? null
                        : parent.getContextView().getName());
            }

            @Override
            public boolean supportsContext(Observation.Context context) {
                return true;
            }
        });
        SentinelObservations observations = new SentinelObservations(registry);

        String result = observations.observe("sentinel.incident.triage",
                () -> observations.observe("sentinel.agent.classify", () -> "BAD_DEPLOY"));

        assertThat(result).isEqualTo("BAD_DEPLOY");
        assertThat(parents).containsEntry("sentinel.incident.triage", null)
                .containsEntry("sentinel.agent.classify", "sentinel.incident.triage");
    }
}
