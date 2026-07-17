package io.mofazzal.sentinel.fleet.seed;

import io.mofazzal.sentinel.fleet.domain.Deployment;
import io.mofazzal.sentinel.fleet.domain.DeploymentStatus;
import io.mofazzal.sentinel.fleet.domain.FleetService;
import io.mofazzal.sentinel.fleet.domain.LogEvent;
import io.mofazzal.sentinel.fleet.domain.LogLevel;
import io.mofazzal.sentinel.fleet.domain.MetricSample;
import io.mofazzal.sentinel.fleet.repository.DeploymentRepository;
import io.mofazzal.sentinel.fleet.repository.FleetServiceRepository;
import io.mofazzal.sentinel.fleet.repository.LogEventRepository;
import io.mofazzal.sentinel.fleet.repository.MetricSampleRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Component
@Profile("seed")
public class SyntheticFleetScenarioSeeder implements ApplicationRunner {

    static final String BAD_DEPLOY_GIT_SHA = "badc0ffee0000000000000000000000000000001";
    static final Instant BAD_DEPLOY_AT = Instant.parse("2026-07-15T12:00:00Z");

    private final FleetServiceRepository serviceRepository;
    private final DeploymentRepository deploymentRepository;
    private final MetricSampleRepository metricRepository;
    private final LogEventRepository logRepository;

    public SyntheticFleetScenarioSeeder(FleetServiceRepository serviceRepository,
                                        DeploymentRepository deploymentRepository,
                                        MetricSampleRepository metricRepository,
                                        LogEventRepository logRepository) {
        this.serviceRepository = serviceRepository;
        this.deploymentRepository = deploymentRepository;
        this.metricRepository = metricRepository;
        this.logRepository = logRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        FleetService payments = serviceRepository.findByName("payments-api")
                .orElseThrow(() -> new IllegalStateException("Reference service payments-api is missing"));

        if (deploymentRepository.existsByServiceIdAndGitSha(payments.getId(), BAD_DEPLOY_GIT_SHA)) {
            return;
        }

        deploymentRepository.saveAll(List.of(
                new Deployment(payments, "2026.07.14.3", "600dfeed000000000000000000000000000001",
                        BAD_DEPLOY_AT.minus(1, ChronoUnit.DAYS), "release-bot", DeploymentStatus.SUCCEEDED),
                new Deployment(payments, "2026.07.15.1", BAD_DEPLOY_GIT_SHA,
                        BAD_DEPLOY_AT, "release-bot", DeploymentStatus.SUCCEEDED)
        ));

        metricRepository.saveAll(metricSamples(payments));
        logRepository.saveAll(logEvents(payments));
    }

    private static List<MetricSample> metricSamples(FleetService service) {
        List<MetricSample> samples = new ArrayList<>();
        for (int minute = -10; minute <= 10; minute++) {
            Instant recordedAt = BAD_DEPLOY_AT.plus(minute, ChronoUnit.MINUTES);
            boolean degraded = minute >= 2;
            samples.add(new MetricSample(service, "error_rate",
                    degraded ? new BigDecimal("0.180000") : new BigDecimal("0.008000"), recordedAt));
            samples.add(new MetricSample(service, "p99_latency_ms",
                    degraded ? new BigDecimal("1850.000000") : new BigDecimal("220.000000"), recordedAt));
            samples.add(new MetricSample(service, "cpu_utilization",
                    degraded ? new BigDecimal("0.920000") : new BigDecimal("0.450000"), recordedAt));
        }
        return samples;
    }

    private static List<LogEvent> logEvents(FleetService service) {
        return List.of(
                new LogEvent(service, LogLevel.INFO, "Deployment 2026.07.15.1 completed",
                        BAD_DEPLOY_AT, "deploy-20260715"),
                new LogEvent(service, LogLevel.WARN, "Payment provider timeout threshold exceeded",
                        BAD_DEPLOY_AT.plus(2, ChronoUnit.MINUTES), "trace-pay-001"),
                new LogEvent(service, LogLevel.ERROR, "Payment authorization failed after provider timeout",
                        BAD_DEPLOY_AT.plus(3, ChronoUnit.MINUTES), "trace-pay-001"),
                new LogEvent(service, LogLevel.ERROR, "Circuit breaker opened for payment-provider",
                        BAD_DEPLOY_AT.plus(4, ChronoUnit.MINUTES), "trace-pay-002"),
                new LogEvent(service, LogLevel.ERROR, "Checkout request failed with upstream timeout",
                        BAD_DEPLOY_AT.plus(5, ChronoUnit.MINUTES), "trace-pay-003")
        );
    }
}
