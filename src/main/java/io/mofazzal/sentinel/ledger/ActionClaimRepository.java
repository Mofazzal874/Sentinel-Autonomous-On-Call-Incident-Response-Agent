package io.mofazzal.sentinel.ledger;

import io.mofazzal.sentinel.fleet.domain.RemediationActionType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ActionClaimRepository extends JpaRepository<ActionClaim, UUID> {
    Optional<ActionClaim> findByFingerprintAndActionType(String fingerprint, RemediationActionType actionType);
    boolean existsByFingerprintAndActionType(String fingerprint, RemediationActionType actionType);
}
