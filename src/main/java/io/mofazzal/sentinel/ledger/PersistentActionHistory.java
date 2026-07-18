package io.mofazzal.sentinel.ledger;

import io.mofazzal.sentinel.fleet.domain.RemediationActionType;
import io.mofazzal.sentinel.guardrail.ActionHistory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class PersistentActionHistory implements ActionHistory {

    private final ActionClaimRepository claims;

    public PersistentActionHistory(ActionClaimRepository claims) {
        this.claims = claims;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean alreadyActiveOrApplied(String incidentFingerprint, RemediationActionType actionType) {
        return claims.existsByFingerprintAndActionType(incidentFingerprint, actionType);
    }
}
