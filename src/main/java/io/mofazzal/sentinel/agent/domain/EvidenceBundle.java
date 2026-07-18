package io.mofazzal.sentinel.agent.domain;

import io.mofazzal.sentinel.tools.DeployQueryTool.DeploySummary;
import io.mofazzal.sentinel.tools.LogSearchTool.ErrorCluster;
import io.mofazzal.sentinel.tools.MetricsQueryTool.MetricWindow;
import io.mofazzal.sentinel.tools.RunbookRetrieveTool.RunbookSummary;

import java.util.List;
import java.util.Objects;

public record EvidenceBundle(
        List<DeploySummary> deployments,
        List<MetricWindow> metrics,
        List<ErrorCluster> logClusters,
        List<RunbookSummary> runbooks
) {
    public EvidenceBundle {
        deployments = List.copyOf(Objects.requireNonNull(deployments, "deployments"));
        metrics = List.copyOf(Objects.requireNonNull(metrics, "metrics"));
        logClusters = List.copyOf(Objects.requireNonNull(logClusters, "logClusters"));
        runbooks = List.copyOf(Objects.requireNonNull(runbooks, "runbooks"));
    }

    public boolean containsRunbook(String title) {
        return title != null && runbooks.stream().anyMatch(runbook -> runbook.title().equals(title));
    }
}
