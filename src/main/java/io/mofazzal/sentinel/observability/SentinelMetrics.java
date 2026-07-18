package io.mofazzal.sentinel.observability;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.mofazzal.sentinel.agent.domain.TriageOutcome;
import io.mofazzal.sentinel.guardrail.GateDecisionType;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class SentinelMetrics {

    private final MeterRegistry registry;

    public SentinelMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void recordTriage(TriageOutcome outcome, Duration duration) {
        String decision = outcome == null ? "FAILED" : outcome.decision().name();
        Timer.builder("sentinel.triage.duration")
                .description("Time from agent workflow start through deterministic remediation decision")
                .tag("decision", decision)
                .register(registry)
                .record(duration);
        if (outcome != null) {
            DistributionSummary.builder("sentinel.evaluator.attempts")
                    .description("Number of bounded proposal/evaluator attempts per triage")
                    .register(registry)
                    .record(outcome.attempts());
        }
    }

    public void recordRemediation(GateDecisionType decision, String outcome) {
        registry.counter("sentinel.remediation.outcome",
                        "decision", decision.name(),
                        "status", outcome)
                .increment();
    }

    public void recordIncidentCreated(String severity) {
        registry.counter("sentinel.incidents.created", "severity", boundedSeverity(severity)).increment();
    }

    public void recordToolCall(String tool) {
        registry.counter("sentinel.agent.tool.calls", "tool", boundedTool(tool)).increment();
    }

    public void recordModelCall(String role) {
        registry.counter("sentinel.agent.model.calls", "role", boundedRole(role)).increment();
    }

    private String boundedTool(String tool) {
        if (tool == null) {
            return "unknown";
        }
        return switch (tool) {
            case "deploy", "metrics", "logs", "runbook" -> tool;
            default -> "unknown";
        };
    }

    private String boundedRole(String role) {
        if (role == null) {
            return "unknown";
        }
        return switch (role) {
            case "router" -> "router";
            case "generator", "proposal-generator" -> "generator";
            case "evaluator", "proposal-evaluator" -> "evaluator";
            case "embedding" -> "embedding";
            default -> "unknown";
        };
    }

    private String boundedSeverity(String severity) {
        if (severity == null) {
            return "unknown";
        }
        return switch (severity) {
            case "SEV1", "SEV2", "SEV3", "SEV4" -> severity;
            default -> "unknown";
        };
    }
}
