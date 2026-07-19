package io.mofazzal.sentinel.fleet.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "runbook")
public class Runbook {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 160)
    private String title;

    @Column(name = "symptom_description", nullable = false, columnDefinition = "text")
    private String symptomDescription;

    @Column(nullable = false, columnDefinition = "text")
    private String steps;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 40)
    private RemediationActionType actionType;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected Runbook() {
    }

    public Runbook(String title, String symptomDescription, String steps,
                   RemediationActionType actionType) {
        this.title = requireText(title, "title");
        this.symptomDescription = requireText(symptomDescription, "symptomDescription");
        this.steps = requireText(steps, "steps");
        this.actionType = Objects.requireNonNull(actionType, "actionType");
    }

    public UUID getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getSymptomDescription() {
        return symptomDescription;
    }

    public String getSteps() {
        return steps;
    }

    public RemediationActionType getActionType() {
        return actionType;
    }

    public Instant getArchivedAt() {
        return archivedAt;
    }

    public long getVersion() {
        return version;
    }

    public void update(String title, String symptomDescription, String steps,
                       RemediationActionType actionType) {
        requireActive();
        this.title = requireText(title, "title");
        this.symptomDescription = requireText(symptomDescription, "symptomDescription");
        this.steps = requireText(steps, "steps");
        this.actionType = Objects.requireNonNull(actionType, "actionType");
    }

    public void archive(Instant archivedAt) {
        requireActive();
        this.archivedAt = Objects.requireNonNull(archivedAt, "archivedAt");
    }

    private void requireActive() {
        if (archivedAt != null) {
            throw new IllegalStateException("runbook is archived");
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
