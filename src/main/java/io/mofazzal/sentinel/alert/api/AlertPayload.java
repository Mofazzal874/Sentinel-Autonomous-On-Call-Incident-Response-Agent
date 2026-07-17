package io.mofazzal.sentinel.alert.api;

import io.mofazzal.sentinel.incident.domain.IncidentSeverity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.Map;

public record AlertPayload(
        @NotBlank @Size(max = 100) String service,
        @NotBlank @Size(max = 120) String alertName,
        @NotNull IncidentSeverity severity,
        @NotNull Instant firedAt,
        @NotBlank @Size(max = 500) String summary,
        @NotNull @Size(max = 20) Map<
                @NotBlank @Size(max = 64) String,
                @NotBlank @Size(max = 200) String> labels
) {
    public AlertPayload {
        labels = labels == null ? null : Map.copyOf(labels);
    }
}
