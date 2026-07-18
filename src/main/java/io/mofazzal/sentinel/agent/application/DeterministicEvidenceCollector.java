package io.mofazzal.sentinel.agent.application;

import io.mofazzal.sentinel.agent.domain.Classification;
import io.mofazzal.sentinel.agent.domain.EvidenceBundle;
import io.mofazzal.sentinel.agent.domain.EvidenceSignal;
import io.mofazzal.sentinel.agent.domain.TriageRequest;
import io.mofazzal.sentinel.agent.security.AgentSecurityContext;
import io.mofazzal.sentinel.observability.SentinelMetrics;
import io.mofazzal.sentinel.tools.DeployQueryTool;
import io.mofazzal.sentinel.tools.LogSearchTool;
import io.mofazzal.sentinel.tools.MetricsQueryTool;
import io.mofazzal.sentinel.tools.RunbookRetrieveTool;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

@Component
public class DeterministicEvidenceCollector implements EvidenceCollector {

    private static final Duration METRIC_HALF_WINDOW = Duration.ofMinutes(15);
    private final DeployQueryTool deployments;
    private final MetricsQueryTool metrics;
    private final LogSearchTool logs;
    private final RunbookRetrieveTool runbooks;
    private final AgentSecurityContext securityContext;
    private final SentinelMetrics observability;

    public DeterministicEvidenceCollector(DeployQueryTool deployments, MetricsQueryTool metrics,
                                          LogSearchTool logs, RunbookRetrieveTool runbooks,
                                          AgentSecurityContext securityContext,
                                          SentinelMetrics observability) {
        this.deployments = deployments;
        this.metrics = metrics;
        this.logs = logs;
        this.runbooks = runbooks;
        this.securityContext = securityContext;
        this.observability = observability;
    }

    @Override
    public EvidenceBundle collect(TriageRequest request, Classification classification) {
        return securityContext.callAsAgent(() -> collectAuthorized(request, classification));
    }

    private EvidenceBundle collectAuthorized(TriageRequest request, Classification classification) {
        var signals = classification.relevantSignals();
        var deployEvidence = List.<DeployQueryTool.DeploySummary>of();
        if (signals.contains(EvidenceSignal.DEPLOYMENTS)) {
            observability.recordToolCall("deploy");
            deployEvidence = deployments.recentDeploys(request.service(), request.occurredAt());
        }
        var metricEvidence = List.<MetricsQueryTool.MetricWindow>of();
        if (signals.contains(EvidenceSignal.METRICS)) {
            observability.recordToolCall("metrics");
            metricEvidence = List.of(metrics.window(request.service(), "error_rate",
                    request.occurredAt().minus(METRIC_HALF_WINDOW),
                    request.occurredAt().plus(METRIC_HALF_WINDOW)));
        }
        var logEvidence = List.<LogSearchTool.ErrorCluster>of();
        if (signals.contains(EvidenceSignal.LOGS)) {
            observability.recordToolCall("logs");
            logEvidence = logs.errorsAround(request.service(), request.occurredAt(), METRIC_HALF_WINDOW);
        }
        var runbookEvidence = List.<RunbookRetrieveTool.RunbookSummary>of();
        if (signals.contains(EvidenceSignal.RUNBOOKS)) {
            observability.recordToolCall("runbook");
            runbookEvidence = runbooks.search(request.summary());
        }
        return new EvidenceBundle(deployEvidence, metricEvidence, logEvidence, runbookEvidence);
    }
}
