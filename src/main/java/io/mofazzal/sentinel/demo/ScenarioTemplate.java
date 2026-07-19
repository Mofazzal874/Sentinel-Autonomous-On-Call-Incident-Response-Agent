package io.mofazzal.sentinel.demo;

import io.mofazzal.sentinel.fleet.domain.FleetService;
import io.mofazzal.sentinel.incident.domain.IncidentSeverity;
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
@Table(name = "demo_scenario_template")
public class ScenarioTemplate {

    public enum ScenarioType { BAD_DEPLOY, DEPENDENCY_TIMEOUT, CAPACITY_SATURATION, CACHE_STALENESS }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "scenario_key", nullable = false, unique = true, length = 80)
    private String scenarioKey;

    @Column(name = "display_name", nullable = false, length = 160)
    private String displayName;

    @Column(nullable = false, length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "scenario_type", nullable = false, length = 40)
    private ScenarioType scenarioType;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_id", nullable = false)
    private FleetService service;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private IncidentSeverity severity;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected ScenarioTemplate() {
    }

    public ScenarioTemplate(String scenarioKey, String displayName, String description,
                            ScenarioType scenarioType, FleetService service,
                            IncidentSeverity severity, boolean enabled, Instant now) {
        this.scenarioKey = text(scenarioKey, "scenarioKey");
        this.displayName = text(displayName, "displayName");
        this.description = text(description, "description");
        this.scenarioType = Objects.requireNonNull(scenarioType, "scenarioType");
        this.service = Objects.requireNonNull(service, "service");
        this.severity = Objects.requireNonNull(severity, "severity");
        this.enabled = enabled;
        this.createdAt = Objects.requireNonNull(now, "now");
        this.updatedAt = now;
    }

    public void update(String scenarioKey, String displayName, String description,
                       ScenarioType scenarioType, FleetService service,
                       IncidentSeverity severity, boolean enabled, Instant now) {
        requireActive();
        this.scenarioKey = text(scenarioKey, "scenarioKey");
        this.displayName = text(displayName, "displayName");
        this.description = text(description, "description");
        this.scenarioType = Objects.requireNonNull(scenarioType, "scenarioType");
        this.service = Objects.requireNonNull(service, "service");
        this.severity = Objects.requireNonNull(severity, "severity");
        this.enabled = enabled;
        this.updatedAt = Objects.requireNonNull(now, "now");
    }

    public void archive(Instant now) {
        requireActive();
        archivedAt = Objects.requireNonNull(now, "now");
        updatedAt = now;
        enabled = false;
    }

    public UUID getId() { return id; }
    public String getScenarioKey() { return scenarioKey; }
    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
    public ScenarioType getScenarioType() { return scenarioType; }
    public FleetService getService() { return service; }
    public IncidentSeverity getSeverity() { return severity; }
    public boolean isEnabled() { return enabled; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getArchivedAt() { return archivedAt; }
    public long getVersion() { return version; }

    private void requireActive() {
        if (archivedAt != null) throw new IllegalStateException("scenario template is archived");
    }

    private static String text(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value;
    }
}
