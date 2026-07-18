package io.mofazzal.sentinel.guardrail;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class RemediationReviewService {

    private final RemediationRequestStore requests;

    public RemediationReviewService(RemediationRequestStore requests) {
        this.requests = requests;
    }

    @PreAuthorize("hasAnyRole('VIEWER','SRE_APPROVER','ADMIN')")
    @Transactional(readOnly = true)
    public RemediationReview get(UUID incidentId) {
        return requests.findReview(incidentId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown remediation request for " + incidentId));
    }
}
