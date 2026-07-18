package io.mofazzal.sentinel.agent.persistence;

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
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "agent_run")
public class AgentRun {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "incident_id", nullable = false)
    private Incident incident;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AgentRunStatus status;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "outcome_reason", columnDefinition = "text")
    private String outcomeReason;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "next_sequence", nullable = false)
    private int nextSequence;

    @Version
    @Column(nullable = false)
    private long version;

    protected AgentRun() {
    }

    public AgentRun(Incident incident, Instant startedAt) {
        this.incident = Objects.requireNonNull(incident, "incident");
        this.startedAt = Objects.requireNonNull(startedAt, "startedAt");
        this.status = AgentRunStatus.RUNNING;
        this.nextSequence = 1;
    }

    public UUID getId() {
        return id;
    }

    public AgentRunStatus getStatus() {
        return status;
    }

    public Incident getIncident() {
        return incident;
    }

    public int nextTranscriptSequence() {
        ensureRunning();
        return nextSequence++;
    }

    public void complete(AgentRunStatus finalStatus, String reason, int attempts, Instant completedAt) {
        ensureRunning();
        if (finalStatus == null || finalStatus == AgentRunStatus.RUNNING) {
            throw new IllegalArgumentException("finalStatus must be terminal");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason must not be blank");
        }
        if (attempts < 0 || attempts > 3) {
            throw new IllegalArgumentException("attempts must be between 0 and 3");
        }
        if (completedAt == null || completedAt.isBefore(startedAt)) {
            throw new IllegalArgumentException("completedAt must not be before startedAt");
        }
        this.status = finalStatus;
        this.outcomeReason = reason;
        this.attemptCount = attempts;
        this.completedAt = completedAt;
    }

    private void ensureRunning() {
        if (status != AgentRunStatus.RUNNING) {
            throw new IllegalStateException("Agent run is already complete");
        }
    }
}
