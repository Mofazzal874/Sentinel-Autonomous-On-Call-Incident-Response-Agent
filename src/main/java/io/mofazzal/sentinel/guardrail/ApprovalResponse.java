package io.mofazzal.sentinel.guardrail;

import java.util.UUID;

public record ApprovalResponse(UUID incidentId, String outcome) {
}
