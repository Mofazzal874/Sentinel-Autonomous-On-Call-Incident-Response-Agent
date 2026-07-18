package io.mofazzal.sentinel.tools;

import io.mofazzal.sentinel.fleet.domain.RemediationActionType;
import io.mofazzal.sentinel.agent.retrieval.RunbookSearchEngine;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RunbookRetrieveToolTest {

    private final RunbookSearchEngine runbooks = mock(RunbookSearchEngine.class);
    private final RunbookRetrieveTool tool = new RunbookRetrieveTool(runbooks);

    @Test
    void returnsStableDtoWithoutEntityLeak() {
        var runbook = new RunbookSearchEngine.RunbookMatch(
                java.util.UUID.randomUUID(), "Rollback a faulty service deployment",
                "Errors rise after deployment", "Verify correlation, then roll back safely",
                RemediationActionType.ROLLBACK_DEPLOYMENT, 0.91);
        when(runbooks.search(any(), org.mockito.ArgumentMatchers.anyInt())).thenReturn(List.of(runbook));

        List<RunbookRetrieveTool.RunbookSummary> result = tool.search(" faulty deployment ");

        assertThat(result).singleElement().satisfies(summary -> {
            assertThat(summary.title()).contains("Rollback");
            assertThat(summary.actionType()).isEqualTo(RemediationActionType.ROLLBACK_DEPLOYMENT);
        });
    }

    @Test
    void rejectsUnboundedSymptomText() {
        assertThatThrownBy(() -> tool.search("x".repeat(201)))
                .isInstanceOf(ToolInputException.class)
                .hasMessageContaining("1-200");
    }
}
