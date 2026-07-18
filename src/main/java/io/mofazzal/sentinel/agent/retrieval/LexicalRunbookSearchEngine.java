package io.mofazzal.sentinel.agent.retrieval;

import io.mofazzal.sentinel.fleet.repository.RunbookRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@ConditionalOnProperty(name = "sentinel.agent.retrieval-mode", havingValue = "lexical", matchIfMissing = true)
public class LexicalRunbookSearchEngine implements RunbookSearchEngine {

    private final RunbookRepository runbooks;

    public LexicalRunbookSearchEngine(RunbookRepository runbooks) {
        this.runbooks = runbooks;
    }

    @Override
    @Transactional(readOnly = true)
    public List<RunbookMatch> search(String symptom, int limit) {
        return runbooks.searchLexical(symptom, PageRequest.of(0, limit)).stream()
                .map(runbook -> new RunbookMatch(runbook.getId(), runbook.getTitle(),
                        runbook.getSymptomDescription(), runbook.getSteps(), runbook.getActionType(), 1.0))
                .toList();
    }
}
