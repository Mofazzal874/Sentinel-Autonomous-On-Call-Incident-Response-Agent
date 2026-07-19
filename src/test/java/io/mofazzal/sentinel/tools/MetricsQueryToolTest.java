package io.mofazzal.sentinel.tools;

import io.mofazzal.sentinel.fleet.domain.FleetService;
import io.mofazzal.sentinel.fleet.domain.MetricSample;
import io.mofazzal.sentinel.fleet.domain.ServiceTier;
import io.mofazzal.sentinel.fleet.domain.Team;
import io.mofazzal.sentinel.fleet.repository.FleetServiceRepository;
import io.mofazzal.sentinel.fleet.repository.MetricSampleRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MetricsQueryToolTest {

    private final FleetServiceRepository services = mock(FleetServiceRepository.class);
    private final MetricSampleRepository metrics = mock(MetricSampleRepository.class);
    private final MetricsQueryTool tool = new MetricsQueryTool(services, metrics);

    @Test
    void downsamplesAndComputesCurrentDeltaAgainstBaseline() {
        FleetService service = service();
        Instant from = Instant.parse("2026-07-15T11:50:00Z");
        Instant to = from.plus(Duration.ofMinutes(30));
        when(services.findByNameAndArchivedAtIsNull("payments-api")).thenReturn(Optional.of(service));
        when(metrics.recentWindow(any(), any(), any(), any(), any()))
                .thenReturn(descendingSamples(service, from));

        MetricsQueryTool.MetricWindow result = tool.window(
                "payments-api", "error_rate", from, to);

        assertThat(result.points()).hasSizeLessThanOrEqualTo(20);
        assertThat(result.points()).isSortedAccordingTo(
                java.util.Comparator.comparing(MetricsQueryTool.MetricPoint::recordedAt));
        assertThat(result.baselineAverage()).isEqualByComparingTo("0.010000");
        assertThat(result.currentAverage()).isEqualByComparingTo("0.200000");
        assertThat(result.percentageDelta()).isEqualByComparingTo("1900.00");
    }

    @Test
    void rejectsUnboundedWindowBeforeQuerying() {
        assertThatThrownBy(() -> tool.window("payments-api", "error_rate",
                Instant.EPOCH, Instant.EPOCH.plus(Duration.ofHours(7))))
                .isInstanceOf(ToolInputException.class)
                .hasMessageContaining("must not exceed");
    }

    private static List<MetricSample> descendingSamples(FleetService service, Instant from) {
        List<MetricSample> samples = new ArrayList<>();
        for (int minute = 0; minute < 30; minute++) {
            BigDecimal value = minute < 15 ? new BigDecimal("0.01") : new BigDecimal("0.20");
            samples.add(new MetricSample(service, "error_rate", value, from.plusSeconds(minute * 60L)));
        }
        java.util.Collections.reverse(samples);
        return samples;
    }

    private static FleetService service() {
        return new FleetService("payments-api", new Team("Payments", "#payments"),
                ServiceTier.CRITICAL, Set.of());
    }
}
