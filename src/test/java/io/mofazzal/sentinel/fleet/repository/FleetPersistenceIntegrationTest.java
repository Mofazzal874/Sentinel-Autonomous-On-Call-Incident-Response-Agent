package io.mofazzal.sentinel.fleet.repository;

import io.mofazzal.sentinel.fleet.domain.Deployment;
import io.mofazzal.sentinel.fleet.domain.DeploymentStatus;
import io.mofazzal.sentinel.fleet.domain.FleetService;
import io.mofazzal.sentinel.fleet.domain.LogEvent;
import io.mofazzal.sentinel.fleet.domain.LogLevel;
import io.mofazzal.sentinel.fleet.domain.MetricSample;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest
class FleetPersistenceIntegrationTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            "pgvector/pgvector:0.8.2-pg17-bookworm"
    );

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private FleetServiceRepository serviceRepository;

    @Autowired
    private DeploymentRepository deploymentRepository;

    @Autowired
    private MetricSampleRepository metricRepository;

    @Autowired
    private LogEventRepository logRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Transactional
    void migrationsAndCorrelationQueryUseRealPostgreSql() {
        String vectorVersion = jdbcTemplate.queryForObject(
                "select extversion from pg_extension where extname = 'vector'", String.class);
        assertThat(vectorVersion).isEqualTo("0.8.2");

        FleetService payments = serviceRepository.findByName("payments-api").orElseThrow();
        Instant spikeAt = Instant.parse("2026-07-15T12:05:00Z");
        Deployment older = new Deployment(payments, "2026.07.14.3", "test-good-sha",
                spikeAt.minus(1, ChronoUnit.DAYS), "test", DeploymentStatus.SUCCEEDED);
        Deployment bad = new Deployment(payments, "2026.07.15.1", "test-bad-sha",
                spikeAt.minus(5, ChronoUnit.MINUTES), "test", DeploymentStatus.SUCCEEDED);
        Deployment afterSpike = new Deployment(payments, "2026.07.15.2", "test-after-sha",
                spikeAt.plus(1, ChronoUnit.MINUTES), "test", DeploymentStatus.SUCCEEDED);
        deploymentRepository.saveAllAndFlush(List.of(older, bad, afterSpike));

        List<Deployment> correlated = deploymentRepository.recentBefore(
                payments.getId(), spikeAt, PageRequest.of(0, 3));

        assertThat(correlated)
                .extracting(Deployment::getGitSha)
                .containsExactly("test-bad-sha", "test-good-sha");

        metricRepository.saveAllAndFlush(List.of(
                new MetricSample(payments, "error_rate", new BigDecimal("0.01"), spikeAt.minusSeconds(60)),
                new MetricSample(payments, "error_rate", new BigDecimal("0.18"), spikeAt),
                new MetricSample(payments, "error_rate", new BigDecimal("0.20"), spikeAt.plusSeconds(60))
        ));
        assertThat(metricRepository.recentWindow(
                payments.getId(), "error_rate", spikeAt.minusSeconds(120), spikeAt.plusSeconds(120),
                PageRequest.of(0, 2)))
                .hasSize(2)
                .extracting(MetricSample::getRecordedAt)
                .containsExactly(spikeAt.plusSeconds(60), spikeAt);

        logRepository.saveAllAndFlush(List.of(
                new LogEvent(payments, LogLevel.WARN, "Timeout threshold exceeded",
                        spikeAt.minusSeconds(30), "test-trace-1"),
                new LogEvent(payments, LogLevel.ERROR, "Payment authorization timed out",
                        spikeAt, "test-trace-1"),
                new LogEvent(payments, LogLevel.ERROR, "Circuit breaker opened",
                        spikeAt.plusSeconds(30), "test-trace-2")
        ));
        assertThat(logRepository.recentWindow(
                payments.getId(), spikeAt.minusSeconds(60), spikeAt.plusSeconds(60),
                PageRequest.of(0, 2)))
                .hasSize(2)
                .extracting(LogEvent::getOccurredAt)
                .containsExactly(spikeAt.plusSeconds(30), spikeAt);
        assertThat(logRepository.searchWindow(
                payments.getId(), "circuit breaker", spikeAt.minusSeconds(60), spikeAt.plusSeconds(60),
                PageRequest.of(0, 5)))
                .singleElement()
                .extracting(LogEvent::getTraceId)
                .isEqualTo("test-trace-2");
    }
}
