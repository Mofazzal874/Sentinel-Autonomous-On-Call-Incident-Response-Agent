package io.mofazzal.sentinel.alert.application;

import io.mofazzal.sentinel.alert.api.AlertAcknowledgement;
import io.mofazzal.sentinel.alert.api.AlertPayload;
import io.mofazzal.sentinel.alert.messaging.AlertPublishException;
import io.mofazzal.sentinel.alert.messaging.TriageCommand;
import io.mofazzal.sentinel.alert.messaging.TriageCommandPublisher;
import io.mofazzal.sentinel.incident.domain.IncidentSeverity;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AlertIngestionServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-18T00:00:00Z");
    private static final String FINGERPRINT = "fingerprint-1";

    private final AlertFingerprinter fingerprinter = mock(AlertFingerprinter.class);
    private final AlertSuppressionService suppression = mock(AlertSuppressionService.class);
    private final TriageCommandPublisher publisher = mock(TriageCommandPublisher.class);
    private final AlertIngestionService ingestion = new AlertIngestionService(
            fingerprinter, suppression, publisher, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void suppressesDuplicateWithoutPublishing() {
        AlertPayload payload = payload();
        when(fingerprinter.fingerprint(payload)).thenReturn(FINGERPRINT);
        when(suppression.claim(FINGERPRINT, null))
                .thenReturn(new AlertSuppressionService.SuppressionDecision(false, 7));

        AlertAcknowledgement result = ingestion.ingest(payload, null);

        assertThat(result.status()).isEqualTo(AlertAcknowledgement.Status.SUPPRESSED);
        assertThat(result.suppressedCount()).isEqualTo(7);
        verify(publisher, never()).publish(any());
    }

    @Test
    void publishesFirstOccurrenceWithFixedReceiveTime() {
        AlertPayload payload = payload();
        when(fingerprinter.fingerprint(payload)).thenReturn(FINGERPRINT);
        when(suppression.claim(FINGERPRINT, "request-1"))
                .thenReturn(new AlertSuppressionService.SuppressionDecision(true, 0));

        AlertAcknowledgement result = ingestion.ingest(payload, "request-1");

        assertThat(result.status()).isEqualTo(AlertAcknowledgement.Status.QUEUED);
        verify(publisher).publish(TriageCommand.create(FINGERPRINT, payload, NOW));
    }

    @Test
    void releasesClaimWhenBrokerPublishFails() {
        AlertPayload payload = payload();
        when(fingerprinter.fingerprint(payload)).thenReturn(FINGERPRINT);
        when(suppression.claim(FINGERPRINT, "request-2"))
                .thenReturn(new AlertSuppressionService.SuppressionDecision(true, 0));
        doThrow(new AlertPublishException("broker down")).when(publisher).publish(any());

        assertThatThrownBy(() -> ingestion.ingest(payload, "request-2"))
                .isInstanceOf(AlertPublishException.class);
        verify(suppression).release(FINGERPRINT, "request-2");
    }

    @Test
    void bypassesUnavailableRedisAndStillPublishes() {
        AlertPayload payload = payload();
        when(fingerprinter.fingerprint(payload)).thenReturn(FINGERPRINT);
        when(suppression.claim(FINGERPRINT, null))
                .thenThrow(new RedisConnectionFailureException("redis down"));

        AlertAcknowledgement result = ingestion.ingest(payload, null);

        assertThat(result.status()).isEqualTo(AlertAcknowledgement.Status.QUEUED);
        verify(publisher).publish(TriageCommand.create(FINGERPRINT, payload, NOW));
    }

    private static AlertPayload payload() {
        return new AlertPayload(
                "payments-api",
                "HighErrorRate",
                IncidentSeverity.SEV2,
                NOW.minusSeconds(30),
                "Error rate is above threshold",
                Map.of("environment", "production")
        );
    }
}
