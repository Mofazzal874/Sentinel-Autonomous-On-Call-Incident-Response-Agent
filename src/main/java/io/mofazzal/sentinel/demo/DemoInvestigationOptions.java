package io.mofazzal.sentinel.demo;

import java.util.List;
import java.util.UUID;

public record DemoInvestigationOptions(
        List<ServiceOption> services,
        List<Choice> symptoms,
        List<Choice> severities,
        List<Choice> signalIntensities,
        List<Choice> customerImpacts,
        List<Choice> deploymentContexts,
        EvidencePlan evidencePlan
) {
    public record ServiceOption(UUID id, String name, String team, String tier, List<String> allowedActions) {
    }
    public record Choice(String value, String label, String description) {
    }
    public record EvidencePlan(int metricSeries, int samplesPerSeries, int logEvents,
                               String persistence, String executionMode) {
    }
}
