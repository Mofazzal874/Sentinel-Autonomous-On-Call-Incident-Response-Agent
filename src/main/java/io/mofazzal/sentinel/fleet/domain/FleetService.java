package io.mofazzal.sentinel.fleet.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
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
import org.hibernate.annotations.BatchSize;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "fleet_service")
public class FleetService {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_team_id", nullable = false)
    private Team ownerTeam;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ServiceTier tier;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "service_allowed_action", joinColumns = @JoinColumn(name = "service_id"))
    @Column(name = "action_type", nullable = false, length = 40)
    @Enumerated(EnumType.STRING)
    @BatchSize(size = 100)
    private Set<RemediationActionType> allowedActions = EnumSet.noneOf(RemediationActionType.class);

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected FleetService() {
    }

    public FleetService(String name, Team ownerTeam, ServiceTier tier, Set<RemediationActionType> allowedActions) {
        this.name = requireText(name);
        this.ownerTeam = Objects.requireNonNull(ownerTeam, "ownerTeam");
        this.tier = Objects.requireNonNull(tier, "tier");
        this.allowedActions = allowedActions == null || allowedActions.isEmpty()
                ? EnumSet.noneOf(RemediationActionType.class)
                : EnumSet.copyOf(allowedActions);
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Team getOwnerTeam() {
        return ownerTeam;
    }

    public ServiceTier getTier() {
        return tier;
    }

    public Set<RemediationActionType> getAllowedActions() {
        return Set.copyOf(allowedActions);
    }

    public Instant getArchivedAt() {
        return archivedAt;
    }

    public long getVersion() {
        return version;
    }

    public void update(String name, Team ownerTeam, ServiceTier tier,
                       Set<RemediationActionType> allowedActions) {
        requireActive();
        this.name = requireText(name);
        this.ownerTeam = Objects.requireNonNull(ownerTeam, "ownerTeam");
        this.tier = Objects.requireNonNull(tier, "tier");
        this.allowedActions = allowedActions == null || allowedActions.isEmpty()
                ? EnumSet.noneOf(RemediationActionType.class)
                : EnumSet.copyOf(allowedActions);
    }

    public void archive(Instant archivedAt) {
        requireActive();
        this.archivedAt = Objects.requireNonNull(archivedAt, "archivedAt");
    }

    private void requireActive() {
        if (archivedAt != null) {
            throw new IllegalStateException("service is archived");
        }
    }

    private static String requireText(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        return value;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof FleetService service && id != null && Objects.equals(id, service.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
