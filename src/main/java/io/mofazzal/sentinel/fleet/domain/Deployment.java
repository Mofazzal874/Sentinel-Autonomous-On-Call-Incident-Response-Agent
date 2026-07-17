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

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "deployment")
public class Deployment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_id", nullable = false)
    private FleetService service;

    @Column(nullable = false, length = 80)
    private String version;

    @Column(name = "git_sha", nullable = false, length = 64)
    private String gitSha;

    @Column(name = "deployed_at", nullable = false)
    private Instant deployedAt;

    @Column(name = "deployed_by", nullable = false, length = 120)
    private String deployedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DeploymentStatus status;

    protected Deployment() {
    }

    public Deployment(FleetService service, String version, String gitSha, Instant deployedAt,
                      String deployedBy, DeploymentStatus status) {
        this.service = Objects.requireNonNull(service, "service");
        this.version = requireText(version, "version");
        this.gitSha = requireText(gitSha, "gitSha");
        this.deployedAt = Objects.requireNonNull(deployedAt, "deployedAt");
        this.deployedBy = requireText(deployedBy, "deployedBy");
        this.status = Objects.requireNonNull(status, "status");
    }

    public UUID getId() {
        return id;
    }

    public FleetService getService() {
        return service;
    }

    public String getVersion() {
        return version;
    }

    public String getGitSha() {
        return gitSha;
    }

    public Instant getDeployedAt() {
        return deployedAt;
    }

    public String getDeployedBy() {
        return deployedBy;
    }

    public DeploymentStatus getStatus() {
        return status;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
