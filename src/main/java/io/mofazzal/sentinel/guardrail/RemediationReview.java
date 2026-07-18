package io.mofazzal.sentinel.guardrail;

import io.mofazzal.sentinel.fleet.domain.RemediationActionType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RemediationReview(
        UUID incidentId,
        String service,
        RemediationActionType actionType,
        String groundedRunbook,
        List<String> steps,
        String rationale,
        String modelRiskNotes,
        double groundingSimilarity,
        Integer authoritativeRiskScore,
        String authoritativeRiskBreakdown,
        RemediationRequestStatus status,
        Instant approvalExpiresAt,
        String decidedBy,
        String decisionNote
) {
}
