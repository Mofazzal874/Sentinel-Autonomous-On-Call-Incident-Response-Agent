package io.mofazzal.sentinel.agent.retrieval;

import io.mofazzal.sentinel.fleet.repository.RunbookRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class RunbookDocumentReader {

    private final RunbookRepository runbooks;

    public RunbookDocumentReader(RunbookRepository runbooks) {
        this.runbooks = runbooks;
    }

    @Transactional(readOnly = true)
    public List<RunbookDocument> readAll() {
        return runbooks.findAll().stream()
                .map(runbook -> new RunbookDocument(runbook.getId(), runbook.getTitle(),
                        runbook.getSymptomDescription(), runbook.getSteps(), runbook.getActionType()))
                .toList();
    }
}
