package io.mofazzal.sentinel.guardrail;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;

@Entity
@Table(name = "safety_control")
public class SafetyControl {

    @Id
    private short id;

    @Column(nullable = false)
    private boolean engaged;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false, length = 120)
    private String updatedBy;

    @Version
    @Column(nullable = false)
    private long version;

    protected SafetyControl() {
    }

    public boolean isEngaged() {
        return engaged;
    }

    public void setEngaged(boolean engaged, String actor, Instant changedAt) {
        if (actor == null || actor.isBlank()) {
            throw new IllegalArgumentException("actor must not be blank");
        }
        if (changedAt == null) {
            throw new IllegalArgumentException("changedAt must not be null");
        }
        this.engaged = engaged;
        this.updatedBy = actor;
        this.updatedAt = changedAt.isBefore(updatedAt) ? updatedAt : changedAt;
    }
}
