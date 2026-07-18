package io.mofazzal.sentinel.observability;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.function.Supplier;

@Component
public class SentinelObservations {

    private final ObservationRegistry registry;

    public SentinelObservations(ObservationRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    public <T> T observe(String name, Supplier<T> work, String... lowCardinalityKeyValues) {
        if (lowCardinalityKeyValues.length % 2 != 0) {
            throw new IllegalArgumentException("Observation key/value arguments must be paired");
        }
        Observation observation = Observation.createNotStarted(name, registry);
        for (int index = 0; index < lowCardinalityKeyValues.length; index += 2) {
            observation.lowCardinalityKeyValue(
                    lowCardinalityKeyValues[index], lowCardinalityKeyValues[index + 1]);
        }
        return observation.observe(work);
    }
}
