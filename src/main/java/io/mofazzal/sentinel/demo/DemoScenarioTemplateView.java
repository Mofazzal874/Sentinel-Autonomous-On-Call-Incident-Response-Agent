package io.mofazzal.sentinel.demo;

import java.util.UUID;

public record DemoScenarioTemplateView(UUID id, String scenarioKey, String displayName,
                                       String description, String scenarioType,
                                       String service, String severity) {
}
