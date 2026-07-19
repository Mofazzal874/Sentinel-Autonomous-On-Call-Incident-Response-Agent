package io.mofazzal.sentinel.demo;

import io.mofazzal.sentinel.incident.domain.IncidentSeverity;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record DemoInvestigationRequest(
        @NotNull UUID serviceId,
        @NotNull ScenarioTemplate.ScenarioType symptom,
        @NotNull IncidentSeverity severity,
        @NotNull SignalIntensity signalIntensity,
        @NotNull CustomerImpact customerImpact,
        @NotNull DeploymentContext deploymentContext
) {
    public enum SignalIntensity { ELEVATED, HIGH, CRITICAL }
    public enum CustomerImpact { DEGRADED, PARTIAL_OUTAGE, FULL_OUTAGE, STALE_RESULTS }
    public enum DeploymentContext { NONE, RECENT_CHANGE }
}
