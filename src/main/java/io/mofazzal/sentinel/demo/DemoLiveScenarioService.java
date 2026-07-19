package io.mofazzal.sentinel.demo;

import io.mofazzal.sentinel.alert.api.AlertAcknowledgement;
import io.mofazzal.sentinel.alert.api.AlertPayload;
import io.mofazzal.sentinel.alert.application.AlertFingerprinter;
import io.mofazzal.sentinel.alert.application.AlertIngestionService;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@Profile("demo")
public class DemoLiveScenarioService {

    private final ScenarioTemplateRepository templates;
    private final DemoLiveSubmissionStore submissions;
    private final DemoSandboxRateLimiter limiter;
    private final DemoScenarioEvidenceSeeder evidence;
    private final AlertFingerprinter fingerprinter;
    private final AlertIngestionService alerts;
    private final Clock clock;

    public DemoLiveScenarioService(ScenarioTemplateRepository templates,
                                   DemoLiveSubmissionStore submissions,
                                   DemoSandboxRateLimiter limiter,
                                   DemoScenarioEvidenceSeeder evidence,
                                   AlertFingerprinter fingerprinter,
                                   AlertIngestionService alerts, Clock clock) {
        this.templates = templates;
        this.submissions = submissions;
        this.limiter = limiter;
        this.evidence = evidence;
        this.fingerprinter = fingerprinter;
        this.alerts = alerts;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<DemoScenarioTemplateView> listTemplates() {
        return templates.findByArchivedAtIsNull(PageRequest.of(0, 20)).stream()
                .filter(ScenarioTemplate::isEnabled)
                .map(template -> new DemoScenarioTemplateView(template.getId(), template.getScenarioKey(),
                        template.getDisplayName(), template.getDescription(),
                        template.getScenarioType().name(), template.getService().getName(),
                        template.getSeverity().name()))
                .toList();
    }

    public DemoLiveSubmissionView submit(UUID templateId, String idempotencyKey, String clientAddress) {
        String normalizedKey = requireIdempotencyKey(idempotencyKey);
        String clientHash = sha256(normalizeAddress(clientAddress));
        String idempotencyHash = sha256(normalizedKey);
        var existing = submissions.findExisting(clientHash, idempotencyHash);
        if (existing.isPresent()) return existing.orElseThrow();

        ScenarioTemplate template = templates.findByIdAndEnabledTrueAndArchivedAtIsNull(templateId)
                .orElseThrow(() -> new DemoScenarioNotFoundException("Unknown or disabled demo scenario"));
        limiter.acquire(clientHash);

        UUID publicId = UUID.randomUUID();
        Instant firedAt = clock.instant();
        AlertPayload payload = payload(template, publicId, firedAt);
        String fingerprint = fingerprinter.fingerprint(payload);
        try {
            submissions.insert(publicId, templateId, fingerprint, clientHash, idempotencyHash, firedAt);
        } catch (DataIntegrityViolationException race) {
            limiter.release();
            return submissions.findExisting(clientHash, idempotencyHash).orElseThrow(() -> race);
        }

        try {
            evidence.seed(publicId, template, firedAt);
            AlertAcknowledgement acknowledgement = alerts.ingest(payload, "demo:" + publicId);
            if (acknowledgement.status() != AlertAcknowledgement.Status.QUEUED) {
                throw new IllegalStateException("A new live scenario was unexpectedly suppressed");
            }
            submissions.markQueued(publicId);
            return submissions.find(publicId).orElseThrow();
        } catch (RuntimeException failure) {
            submissions.fail(fingerprint, failure.getMessage(), clock.instant());
            limiter.release();
            throw failure;
        }
    }

    public DemoLiveSubmissionView status(UUID publicId) {
        return submissions.find(publicId)
                .orElseThrow(() -> new DemoScenarioNotFoundException("Unknown live demo submission"));
    }

    private static AlertPayload payload(ScenarioTemplate template, UUID publicId, Instant firedAt) {
        String alertName;
        String summary;
        switch (template.getScenarioType()) {
            case BAD_DEPLOY -> {
                alertName = "HighErrorRateAfterDeploy";
                summary = "Error rate and p99 latency rose immediately after a synthetic release.";
            }
            case DEPENDENCY_TIMEOUT -> {
                alertName = "RequiredDependencyTimeout";
                summary = "A required upstream dependency is timing out while local health remains bounded.";
            }
            case CAPACITY_SATURATION -> {
                alertName = "ServiceCapacitySaturation";
                summary = "Queue depth, CPU saturation, and request latency exceeded synthetic service objectives.";
            }
            case CACHE_STALENESS -> {
                alertName = "StaleCacheReads";
                summary = "Cached responses lag behind the healthy synthetic source of truth.";
            }
            default -> throw new IllegalStateException("Unsupported fixed scenario type");
        }
        return new AlertPayload(template.getService().getName(), alertName, template.getSeverity(),
                firedAt, summary, Map.of("environment", "portfolio-sandbox",
                "scenario", template.getScenarioKey(), "demo_run_id", publicId.toString()));
    }

    private static String requireIdempotencyKey(String value) {
        if (value == null || value.isBlank() || value.trim().length() > 100) {
            throw new IllegalArgumentException("Idempotency-Key is required and must be at most 100 characters");
        }
        return value.trim();
    }

    private static String normalizeAddress(String value) {
        if (value == null || value.isBlank()) return "unknown-client";
        return value.trim().toLowerCase(Locale.ROOT).substring(0, Math.min(value.trim().length(), 200));
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the Java platform", exception);
        }
    }
}
