package io.mofazzal.sentinel.demo;

import io.mofazzal.sentinel.fleet.domain.FleetService;
import io.mofazzal.sentinel.incident.domain.IncidentSeverity;

record DemoInvestigationConfiguration(
        ScenarioTemplate template,
        FleetService service,
        ScenarioTemplate.ScenarioType symptom,
        IncidentSeverity severity,
        DemoInvestigationRequest.SignalIntensity signalIntensity,
        DemoInvestigationRequest.CustomerImpact customerImpact,
        DemoInvestigationRequest.DeploymentContext deploymentContext,
        String title,
        String summary
) {
}
