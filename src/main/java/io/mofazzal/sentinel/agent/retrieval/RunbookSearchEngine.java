package io.mofazzal.sentinel.agent.retrieval;

import io.mofazzal.sentinel.fleet.domain.RemediationActionType;

import java.util.List;
import java.util.UUID;

public interface RunbookSearchEngine {
    List<RunbookMatch> search(String symptom, int limit);

    record RunbookMatch(
            UUID id,
            String title,
            String symptomDescription,
            String steps,
            RemediationActionType actionType,
            double similarity
    ) {
    }
}
