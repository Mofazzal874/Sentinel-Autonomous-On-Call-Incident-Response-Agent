package io.mofazzal.sentinel.fleet.domain;

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
@Table(name = "service_dependency")
public class ServiceDependency {

    public enum Criticality { REQUIRED, DEGRADED_OK }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "caller_service_id", nullable = false)
    private FleetService callerService;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "dependency_service_id", nullable = false)
    private FleetService dependencyService;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Criticality criticality;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected ServiceDependency() {
    }

    public ServiceDependency(FleetService callerService, FleetService dependencyService,
                             Criticality criticality, Instant createdAt) {
        this.callerService = Objects.requireNonNull(callerService, "callerService");
        this.dependencyService = Objects.requireNonNull(dependencyService, "dependencyService");
        if (callerService == dependencyService) {
            throw new IllegalArgumentException("a service cannot depend on itself");
        }
        this.criticality = Objects.requireNonNull(criticality, "criticality");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    public UUID getId() { return id; }
    public FleetService getCallerService() { return callerService; }
    public FleetService getDependencyService() { return dependencyService; }
    public Criticality getCriticality() { return criticality; }
    public Instant getCreatedAt() { return createdAt; }
    public long getVersion() { return version; }
}
