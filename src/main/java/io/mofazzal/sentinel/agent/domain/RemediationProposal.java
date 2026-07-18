package io.mofazzal.sentinel.agent.domain;

import io.mofazzal.sentinel.fleet.domain.RemediationActionType;

import java.util.List;
import java.util.Objects;

public record RemediationProposal(
        RemediationActionType actionType,
        String runbookTitle,
        List<String> steps,
        String rationale,
        String riskNotes
) {
    private static final int MAX_STEPS = 10;

    public RemediationProposal {
        Objects.requireNonNull(actionType, "actionType");
        runbookTitle = requireText(runbookTitle, "runbookTitle");
        steps = List.copyOf(Objects.requireNonNull(steps, "steps"));
        if (steps.isEmpty() || steps.size() > MAX_STEPS || steps.stream().anyMatch(step -> step == null || step.isBlank())) {
            throw new IllegalArgumentException("steps must contain between 1 and 10 non-blank items");
        }
        rationale = requireText(rationale, "rationale");
        riskNotes = requireText(riskNotes, "riskNotes");
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
