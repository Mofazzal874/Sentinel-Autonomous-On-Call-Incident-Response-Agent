package io.mofazzal.sentinel.demo;

import java.time.Instant;

public record DemoSystemOverview(
        int teams,
        int services,
        int dependencies,
        int deployments,
        int metricSamples,
        int logEvents,
        int incidents,
        int runbooks,
        int publicScenarios,
        int liveRuns,
        int ledgerEvents,
        String executionMode,
        String modelAuthority,
        Instant measuredAt
) {
}
