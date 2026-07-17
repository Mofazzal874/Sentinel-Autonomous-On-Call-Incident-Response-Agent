package io.mofazzal.sentinel.alert.application;

import io.mofazzal.sentinel.alert.api.AlertPayload;
import io.mofazzal.sentinel.incident.domain.IncidentSeverity;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AlertFingerprinterTest {

    private final AlertFingerprinter fingerprinter = new AlertFingerprinter();

    @Test
    void labelOrderAndPresentationDifferencesDoNotChangeFingerprint() {
        Map<String, String> firstLabels = new LinkedHashMap<>();
        firstLabels.put("region", "US East");
        firstLabels.put("environment", "Production");

        Map<String, String> secondLabels = new LinkedHashMap<>();
        secondLabels.put("ENVIRONMENT", "  production ");
        secondLabels.put("REGION", "us   east");

        AlertPayload first = payload(" payments-api ", "High Error Rate", firstLabels);
        AlertPayload second = payload("PAYMENTS-API", " high   error rate ", secondLabels);

        assertThat(fingerprinter.fingerprint(first))
                .isEqualTo(fingerprinter.fingerprint(second))
                .hasSize(64);
    }

    @Test
    void semanticLabelChangeCreatesDifferentFingerprint() {
        AlertPayload production = payload("payments-api", "High Error Rate",
                Map.of("environment", "production"));
        AlertPayload staging = payload("payments-api", "High Error Rate",
                Map.of("environment", "staging"));

        assertThat(fingerprinter.fingerprint(production))
                .isNotEqualTo(fingerprinter.fingerprint(staging));
    }

    private static AlertPayload payload(String service, String alertName, Map<String, String> labels) {
        return new AlertPayload(
                service,
                alertName,
                IncidentSeverity.SEV2,
                Instant.parse("2026-07-18T00:00:00Z"),
                "Error rate is above threshold",
                labels
        );
    }
}
