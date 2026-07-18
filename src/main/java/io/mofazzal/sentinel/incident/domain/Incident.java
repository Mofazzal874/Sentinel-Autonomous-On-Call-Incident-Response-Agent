package io.mofazzal.sentinel.incident.domain;

import io.mofazzal.sentinel.fleet.domain.Deployment;
import io.mofazzal.sentinel.fleet.domain.FleetService;
import io.mofazzal.sentinel.fleet.domain.Runbook;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "incident")
public class Incident {

    private static final Map<IncidentStatus, Set<IncidentStatus>> ALLOWED_TRANSITIONS = allowedTransitions();

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 128)
    private String fingerprint;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_id", nullable = false)
    private FleetService service;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private IncidentStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private IncidentSeverity severity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "correlated_deployment_id")
    private Deployment correlatedDeployment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "proposed_runbook_id")
    private Runbook proposedRunbook;

    @Column(name = "risk_score", precision = 5, scale = 2)
    private BigDecimal riskScore;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected Incident() {
    }

    public Incident(String fingerprint, FleetService service, IncidentSeverity severity, Instant createdAt) {
        this.fingerprint = requireText(fingerprint);
        this.service = Objects.requireNonNull(service, "service");
        this.severity = Objects.requireNonNull(severity, "severity");
        this.status = IncidentStatus.OPEN;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public String getFingerprint() {
        return fingerprint;
    }

    public FleetService getService() {
        return service;
    }

    public IncidentSeverity getSeverity() {
        return severity;
    }

    public IncidentStatus getStatus() {
        return status;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void transitionTo(IncidentStatus nextStatus, Instant changedAt) {
        Objects.requireNonNull(nextStatus, "nextStatus");
        Objects.requireNonNull(changedAt, "changedAt");
        if (changedAt.isBefore(updatedAt)) {
            throw new IllegalArgumentException("changedAt must not be before the previous update");
        }
        if (!ALLOWED_TRANSITIONS.getOrDefault(status, Set.of()).contains(nextStatus)) {
            throw new IllegalStateException("Invalid incident transition: " + status + " -> " + nextStatus);
        }
        status = nextStatus;
        updatedAt = changedAt;
    }

    private static String requireText(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("fingerprint must not be blank");
        }
        return value;
    }

    private static Map<IncidentStatus, Set<IncidentStatus>> allowedTransitions() {
        Map<IncidentStatus, Set<IncidentStatus>> transitions = new EnumMap<>(IncidentStatus.class);
        transitions.put(IncidentStatus.OPEN,
                EnumSet.of(IncidentStatus.TRIAGING, IncidentStatus.ESCALATED));
        transitions.put(IncidentStatus.TRIAGING,
                EnumSet.of(IncidentStatus.AWAITING_APPROVAL, IncidentStatus.REMEDIATING, IncidentStatus.ESCALATED));
        transitions.put(IncidentStatus.AWAITING_APPROVAL,
                EnumSet.of(IncidentStatus.REMEDIATING, IncidentStatus.ESCALATED));
        transitions.put(IncidentStatus.REMEDIATING,
                EnumSet.of(IncidentStatus.RESOLVED, IncidentStatus.ESCALATED));
        transitions.put(IncidentStatus.RESOLVED, EnumSet.noneOf(IncidentStatus.class));
        transitions.put(IncidentStatus.ESCALATED, EnumSet.noneOf(IncidentStatus.class));
        return Map.copyOf(transitions);
    }
}
