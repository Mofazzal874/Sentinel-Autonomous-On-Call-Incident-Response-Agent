package io.mofazzal.sentinel.agent.retrieval;

import io.mofazzal.sentinel.fleet.domain.RemediationActionType;

import java.util.UUID;

public record RunbookDocument(
        UUID id,
        String title,
        String symptomDescription,
        String steps,
        RemediationActionType actionType
) {
    public String embeddingContent() {
        return "Title: " + title + "\nSymptoms: " + symptomDescription + "\nProcedure: " + steps;
    }
}
