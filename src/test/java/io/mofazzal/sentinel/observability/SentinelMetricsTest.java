package io.mofazzal.sentinel.observability;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.mofazzal.sentinel.agent.domain.TriageOutcome;
import io.mofazzal.sentinel.guardrail.GateDecisionType;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class SentinelMetricsTest {

    @Test
    void recordsBoundedTriageRemediationToolAndModelDimensions() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        SentinelMetrics metrics = new SentinelMetrics(registry);
        TriageOutcome outcome = new TriageOutcome(
                TriageOutcome.Decision.ESCALATED, null, null, 0.0,
                "No grounded runbook", 2);

        metrics.recordTriage(outcome, Duration.ofMillis(125));
        metrics.recordRemediation(GateDecisionType.ESCALATE, "SUCCESS");
        metrics.recordIncidentCreated("SEV2");
        metrics.recordToolCall("logs");
        metrics.recordToolCall("attacker-controlled-tool-name");
        metrics.recordModelCall("router");
        metrics.recordModelCall(null);

        assertThat(registry.get("sentinel.triage.duration")
                .tag("decision", "ESCALATED").timer().count()).isOne();
        assertThat(registry.get("sentinel.evaluator.attempts").summary().totalAmount())
                .isEqualTo(2.0);
        assertThat(registry.get("sentinel.remediation.outcome")
                .tags("decision", "ESCALATE", "status", "SUCCESS").counter().count()).isOne();
        assertThat(registry.get("sentinel.incidents.created").tag("severity", "SEV2").counter().count()).isOne();
        assertThat(registry.get("sentinel.agent.tool.calls").tag("tool", "logs").counter().count()).isOne();
        assertThat(registry.get("sentinel.agent.tool.calls").tag("tool", "unknown").counter().count()).isOne();
        assertThat(registry.get("sentinel.agent.model.calls").tag("role", "router").counter().count()).isOne();
        assertThat(registry.get("sentinel.agent.model.calls").tag("role", "unknown").counter().count()).isOne();
    }
}
