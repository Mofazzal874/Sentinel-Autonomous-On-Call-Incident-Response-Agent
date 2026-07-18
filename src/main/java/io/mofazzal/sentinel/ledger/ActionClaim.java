package io.mofazzal.sentinel.ledger;

import io.mofazzal.sentinel.fleet.domain.RemediationActionType;
import io.mofazzal.sentinel.incident.domain.Incident;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "action_claim")
public class ActionClaim {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "incident_id", nullable = false)
    private Incident incident;

    @Column(nullable = false, length = 128)
    private String fingerprint;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 40)
    private RemediationActionType actionType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ActionClaimState state;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(columnDefinition = "text")
    private String result;

    @Version
    @Column(nullable = false)
    private long version;

    protected ActionClaim() {
    }

    public UUID getId() {
        return id;
    }

    public ActionClaimState getState() {
        return state;
    }
}
