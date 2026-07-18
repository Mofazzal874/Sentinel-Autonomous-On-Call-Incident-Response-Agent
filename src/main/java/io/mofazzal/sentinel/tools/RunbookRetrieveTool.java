package io.mofazzal.sentinel.tools;

import io.mofazzal.sentinel.fleet.domain.RemediationActionType;
import io.mofazzal.sentinel.fleet.domain.Runbook;
import io.mofazzal.sentinel.fleet.repository.RunbookRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class RunbookRetrieveTool {

    private static final int RESULT_LIMIT = 5;
    private final RunbookRepository runbooks;

    public RunbookRetrieveTool(RunbookRepository runbooks) {
        this.runbooks = runbooks;
    }

    @PreAuthorize("hasAnyRole('VIEWER','SRE_APPROVER','ADMIN','AGENT')")
    @Transactional(readOnly = true)
    public List<RunbookSummary> search(String symptom) {
        String query = ToolInputs.boundedText(symptom, "symptom", 200);
        return runbooks.searchLexical(query, PageRequest.of(0, RESULT_LIMIT))
                .stream()
                .map(RunbookSummary::from)
                .toList();
    }

    public record RunbookSummary(
            String title,
            String symptomDescription,
            String steps,
            RemediationActionType actionType
    ) {
        static RunbookSummary from(Runbook runbook) {
            return new RunbookSummary(runbook.getTitle(), runbook.getSymptomDescription(),
                    runbook.getSteps(), runbook.getActionType());
        }
    }
}
