package io.mofazzal.sentinel.guardrail;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/incidents")
public class RemediationApprovalController {

    private final RemediationApprovalService approvals;
    private final RemediationReviewService reviews;

    public RemediationApprovalController(RemediationApprovalService approvals,
                                         RemediationReviewService reviews) {
        this.approvals = approvals;
        this.reviews = reviews;
    }

    @PostMapping("/{incidentId}/approve")
    public ResponseEntity<ApprovalResponse> decide(@PathVariable UUID incidentId,
                                                   @Valid @RequestBody ApprovalRequest request,
                                                   Authentication authentication) {
        return ResponseEntity.ok(approvals.decide(incidentId, request, authentication.getName()));
    }

    @GetMapping("/{incidentId}/remediation")
    public RemediationReview review(@PathVariable UUID incidentId) {
        return reviews.get(incidentId);
    }
}
