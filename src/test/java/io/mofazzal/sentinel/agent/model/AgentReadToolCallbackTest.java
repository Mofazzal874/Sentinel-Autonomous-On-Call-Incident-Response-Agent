package io.mofazzal.sentinel.agent.model;

import io.mofazzal.sentinel.agent.retrieval.RunbookSearchEngine;
import io.mofazzal.sentinel.fleet.repository.DeploymentRepository;
import io.mofazzal.sentinel.fleet.repository.FleetServiceRepository;
import io.mofazzal.sentinel.fleet.repository.LogEventRepository;
import io.mofazzal.sentinel.fleet.repository.MetricSampleRepository;
import io.mofazzal.sentinel.tools.DeployQueryTool;
import io.mofazzal.sentinel.tools.LogSearchTool;
import io.mofazzal.sentinel.tools.MetricsQueryTool;
import io.mofazzal.sentinel.tools.RunbookRetrieveTool;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AgentReadToolCallbackTest {

    @Test
    void exposesOnlyFourBoundedReadMethodsToSpringAi() {
        FleetServiceRepository services = mock(FleetServiceRepository.class);
        var callbacks = MethodToolCallbackProvider.builder().toolObjects(
                new DeployQueryTool(services, mock(DeploymentRepository.class)),
                new MetricsQueryTool(services, mock(MetricSampleRepository.class)),
                new LogSearchTool(services, mock(LogEventRepository.class)),
                new RunbookRetrieveTool(mock(RunbookSearchEngine.class)))
                .build()
                .getToolCallbacks();

        assertThat(Arrays.stream(callbacks)
                .map(callback -> callback.getToolDefinition().name()))
                .containsExactlyInAnyOrder("recentDeploys", "window", "errorsAround", "search");
        assertThat(Arrays.stream(callbacks)
                .map(callback -> callback.getToolDefinition().description()))
                .allMatch(description -> description.toLowerCase().contains("bounded")
                        || description.toLowerCase().contains("at most"));
    }
}
