package io.mofazzal.sentinel.alert.application;

import io.mofazzal.sentinel.alert.api.AlertAcknowledgement;
import io.mofazzal.sentinel.alert.api.AlertPayload;
import io.mofazzal.sentinel.alert.messaging.AlertPublishException;
import io.mofazzal.sentinel.alert.messaging.TriageCommand;
import io.mofazzal.sentinel.alert.messaging.TriageCommandPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;

@Service
public class AlertIngestionService {

    private static final Logger logger = LoggerFactory.getLogger(AlertIngestionService.class);

    private final AlertFingerprinter fingerprinter;
    private final AlertSuppressionService suppressionService;
    private final TriageCommandPublisher publisher;
    private final Clock clock;

    public AlertIngestionService(AlertFingerprinter fingerprinter,
                                 AlertSuppressionService suppressionService,
                                 TriageCommandPublisher publisher,
                                 Clock clock) {
        this.fingerprinter = fingerprinter;
        this.suppressionService = suppressionService;
        this.publisher = publisher;
        this.clock = clock;
    }

    public AlertAcknowledgement ingest(AlertPayload payload, String idempotencyKey) {
        String fingerprint = fingerprinter.fingerprint(payload);
        AlertSuppressionService.SuppressionDecision decision = claimOrBypass(fingerprint, idempotencyKey);
        if (!decision.firstOccurrence()) {
            return AlertAcknowledgement.suppressed(fingerprint, decision.suppressedCount());
        }

        Instant receivedAt = clock.instant();
        try {
            publisher.publish(TriageCommand.create(fingerprint, payload, receivedAt));
            return AlertAcknowledgement.queued(fingerprint);
        } catch (AlertPublishException exception) {
            releaseBestEffort(fingerprint, idempotencyKey);
            throw exception;
        }
    }

    private AlertSuppressionService.SuppressionDecision claimOrBypass(
            String fingerprint, String idempotencyKey) {
        try {
            return suppressionService.claim(fingerprint, idempotencyKey);
        } catch (DataAccessException exception) {
            logger.warn("Redis suppression unavailable; relying on database idempotency", exception);
            return AlertSuppressionService.SuppressionDecision.bypassed();
        }
    }

    private void releaseBestEffort(String fingerprint, String idempotencyKey) {
        try {
            suppressionService.release(fingerprint, idempotencyKey);
        } catch (DataAccessException exception) {
            logger.warn("Could not release Redis suppression claim after publish failure", exception);
        }
    }
}
