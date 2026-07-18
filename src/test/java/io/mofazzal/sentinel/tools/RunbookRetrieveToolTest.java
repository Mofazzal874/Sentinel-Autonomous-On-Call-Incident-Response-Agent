package io.mofazzal.sentinel.tools;

import io.mofazzal.sentinel.fleet.domain.RemediationActionType;
import io.mofazzal.sentinel.fleet.domain.Runbook;
import io.mofazzal.sentinel.fleet.repository.RunbookRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RunbookRetrieveToolTest {

    private final RunbookRepository runbooks = mock(RunbookRepository.class);
    private final RunbookRetrieveTool tool = new RunbookRetrieveTool(runbooks);

    @Test
    void returnsStableDtoWithoutEntityLeak() {
        Runbook runbook = mock(Runbook.class);
        when(runbook.getTitle()).thenReturn("Rollback a faulty service deployment");
        when(runbook.getSymptomDescription()).thenReturn("Errors rise after deployment");
        when(runbook.getSteps()).thenReturn("Verify correlation, then roll back safely");
        when(runbook.getActionType()).thenReturn(RemediationActionType.ROLLBACK_DEPLOYMENT);
        when(runbooks.searchLexical(any(), any())).thenReturn(List.of(runbook));

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
