package io.mofazzal.sentinel.tools;

import io.mofazzal.sentinel.fleet.domain.RemediationActionType;
import io.mofazzal.sentinel.agent.retrieval.RunbookSearchEngine;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.ai.tool.annotation.Tool;

import java.util.List;

@Component
public class RunbookRetrieveTool {

    private static final int RESULT_LIMIT = 5;
    private final RunbookSearchEngine runbooks;

    public RunbookRetrieveTool(RunbookSearchEngine runbooks) {
        this.runbooks = runbooks;
    }

    @PreAuthorize("hasAnyRole('VIEWER','SRE_APPROVER','ADMIN','AGENT')")
    @Tool(description = "Retrieve at most five remediation runbooks relevant to a symptom description.")
    public List<RunbookSummary> search(String symptom) {
        String query = ToolInputs.boundedText(symptom, "symptom", 200);
        return runbooks.search(query, RESULT_LIMIT)
                .stream()
                .map(RunbookSummary::from)
                .toList();
    }

    public record RunbookSummary(
            java.util.UUID id,
            String title,
            String symptomDescription,
            String steps,
            RemediationActionType actionType,
            double similarity
    ) {
        static RunbookSummary from(RunbookSearchEngine.RunbookMatch runbook) {
            return new RunbookSummary(runbook.id(), runbook.title(), runbook.symptomDescription(),
                    runbook.steps(), runbook.actionType(), runbook.similarity());
        }
    }
}
