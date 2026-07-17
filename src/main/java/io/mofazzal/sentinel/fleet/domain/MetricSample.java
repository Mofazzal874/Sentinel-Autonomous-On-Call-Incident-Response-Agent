package io.mofazzal.sentinel.fleet.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "metric_sample")
public class MetricSample {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_id", nullable = false)
    private FleetService service;

    @Column(name = "metric_name", nullable = false, length = 100)
    private String metricName;

    @Column(nullable = false, precision = 18, scale = 6)
    private BigDecimal value;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    protected MetricSample() {
    }

    public MetricSample(FleetService service, String metricName, BigDecimal value, Instant recordedAt) {
        this.service = Objects.requireNonNull(service, "service");
        this.metricName = requireText(metricName);
        this.value = Objects.requireNonNull(value, "value");
        this.recordedAt = Objects.requireNonNull(recordedAt, "recordedAt");
    }

    public UUID getId() {
        return id;
    }

    public FleetService getService() {
        return service;
    }

    public String getMetricName() {
        return metricName;
    }

    public BigDecimal getValue() {
        return value;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }

    private static String requireText(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("metricName must not be blank");
        }
        return value;
    }
}
