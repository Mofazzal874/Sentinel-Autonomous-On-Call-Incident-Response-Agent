package io.mofazzal.sentinel.fleet.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

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

    protected Runbook() {
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
}
