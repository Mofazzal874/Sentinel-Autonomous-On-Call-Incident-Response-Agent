package io.mofazzal.sentinel.tools;

import io.mofazzal.sentinel.fleet.domain.FleetService;
import io.mofazzal.sentinel.fleet.domain.LogEvent;
import io.mofazzal.sentinel.fleet.domain.LogLevel;
import io.mofazzal.sentinel.fleet.domain.ServiceTier;
import io.mofazzal.sentinel.fleet.domain.Team;
import io.mofazzal.sentinel.fleet.repository.FleetServiceRepository;
import io.mofazzal.sentinel.fleet.repository.LogEventRepository;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LogSearchToolTest {

    private final FleetServiceRepository services = mock(FleetServiceRepository.class);
    private final LogEventRepository logs = mock(LogEventRepository.class);
    private final LogSearchTool tool = new LogSearchTool(services, logs);

    @Test
    void clustersEquivalentErrorsAndBoundsTraceEvidence() {
        FleetService service = service();
        Instant at = Instant.parse("2026-07-15T12:05:00Z");
        when(services.findByName("payments-api")).thenReturn(Optional.of(service));
        when(logs.recentWindowByLevel(any(), any(), any(), any(), any())).thenReturn(List.of(
                new LogEvent(service, LogLevel.ERROR, "Request 123 timed out", at, "trace-1"),
                new LogEvent(service, LogLevel.ERROR, "Request 456 timed out", at.minusSeconds(1), "trace-2")
        ));

        List<LogSearchTool.ErrorCluster> result = tool.errorsAround(
                "payments-api", at, Duration.ofMinutes(10));

        assertThat(result).singleElement().satisfies(cluster -> {
            assertThat(cluster.signature()).isEqualTo("request {n} timed out");
            assertThat(cluster.count()).isEqualTo(2);
            assertThat(cluster.traceIds()).containsExactly("trace-1", "trace-2");
        });
    }

    @Test
    void rejectsExcessiveHalfWindow() {
        assertThatThrownBy(() -> tool.errorsAround(
                "payments-api", Instant.now(), Duration.ofHours(2)))
                .isInstanceOf(ToolInputException.class)
                .hasMessageContaining("1h");
    }

    private static FleetService service() {
        return new FleetService("payments-api", new Team("Payments", "#payments"),
                ServiceTier.CRITICAL, Set.of());
    }
}
