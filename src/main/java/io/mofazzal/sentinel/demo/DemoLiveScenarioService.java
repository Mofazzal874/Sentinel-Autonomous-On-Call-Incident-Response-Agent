package io.mofazzal.sentinel.demo;

import io.mofazzal.sentinel.alert.api.AlertAcknowledgement;
import io.mofazzal.sentinel.alert.api.AlertPayload;
import io.mofazzal.sentinel.alert.application.AlertFingerprinter;
import io.mofazzal.sentinel.alert.application.AlertIngestionService;
import io.mofazzal.sentinel.fleet.repository.FleetServiceRepository;
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
    private final FleetServiceRepository services;
    private final DemoLiveSubmissionStore submissions;
    private final DemoSandboxRateLimiter limiter;
    private final DemoScenarioEvidenceSeeder evidence;
    private final AlertFingerprinter fingerprinter;
    private final AlertIngestionService alerts;
    private final Clock clock;

    public DemoLiveScenarioService(ScenarioTemplateRepository templates, FleetServiceRepository services,
                                   DemoLiveSubmissionStore submissions,
                                   DemoSandboxRateLimiter limiter,
                                   DemoScenarioEvidenceSeeder evidence,
                                   AlertFingerprinter fingerprinter,
                                   AlertIngestionService alerts, Clock clock) {
        this.templates = templates;
        this.services = services;
        this.submissions = submissions;
        this.limiter = limiter;
        this.evidence = evidence;
        this.fingerprinter = fingerprinter;
        this.alerts = alerts;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public DemoInvestigationOptions options() {
        var serviceOptions = services.findAllByArchivedAtIsNullOrderByNameAsc().stream()
                .map(service -> new DemoInvestigationOptions.ServiceOption(service.getId(), service.getName(),
                        service.getOwnerTeam().getName(), service.getTier().name(),
                        service.getAllowedActions().stream().map(Enum::name).sorted().toList()))
                .toList();
        return new DemoInvestigationOptions(serviceOptions,
                List.of(
                        choice("BAD_DEPLOY", "Release regression", "Errors or latency begin after a recent deployment."),
                        choice("DEPENDENCY_TIMEOUT", "Dependency timeout", "A required upstream service stops responding in time."),
                        choice("CAPACITY_SATURATION", "Capacity saturation", "CPU, queue depth, and tail latency rise together."),
                        choice("CACHE_STALENESS", "Stale data", "Cached results disagree with the healthy source of truth.")),
                List.of(choice("SEV1", "SEV1 · Critical", "Widespread outage or severe business impact."),
                        choice("SEV2", "SEV2 · Major", "Significant partial outage requiring rapid response."),
                        choice("SEV3", "SEV3 · Moderate", "Degradation with a viable workaround."),
                        choice("SEV4", "SEV4 · Low", "Limited impact or early warning.")),
                List.of(choice("ELEVATED", "Elevated", "A clear but moderate deviation from baseline."),
                        choice("HIGH", "High", "Strong correlated signals across multiple sources."),
                        choice("CRITICAL", "Critical", "Extreme signal values and immediate customer risk.")),
                List.of(choice("DEGRADED", "Degraded", "Requests still succeed but performance is worse."),
                        choice("PARTIAL_OUTAGE", "Partial outage", "A meaningful subset of requests fail."),
                        choice("FULL_OUTAGE", "Full outage", "The service is unavailable for most users."),
                        choice("STALE_RESULTS", "Stale results", "Users receive outdated but otherwise valid data.")),
                List.of(choice("NONE", "No recent change", "No deployment is present in the evidence window."),
                        choice("RECENT_CHANGE", "Recent deployment", "A successful release appears five minutes before the alert.")),
                new DemoInvestigationOptions.EvidencePlan(5, 12, 8, "PostgreSQL", "DRY_RUN"));
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
        var configuration = new DemoInvestigationConfiguration(template, template.getService(),
                template.getScenarioType(), template.getSeverity(), DemoInvestigationRequest.SignalIntensity.HIGH,
                template.getScenarioType() == ScenarioTemplate.ScenarioType.CACHE_STALENESS
                        ? DemoInvestigationRequest.CustomerImpact.STALE_RESULTS
                        : DemoInvestigationRequest.CustomerImpact.PARTIAL_OUTAGE,
                template.getScenarioType() == ScenarioTemplate.ScenarioType.BAD_DEPLOY
                        ? DemoInvestigationRequest.DeploymentContext.RECENT_CHANGE
                        : DemoInvestigationRequest.DeploymentContext.NONE,
                template.getDisplayName(), template.getDescription());
        return submit(configuration, clientHash, idempotencyHash);
    }

    public DemoLiveSubmissionView submit(DemoInvestigationRequest request, String idempotencyKey,
                                         String clientAddress) {
        String normalizedKey = requireIdempotencyKey(idempotencyKey);
        String clientHash = sha256(normalizeAddress(clientAddress));
        String idempotencyHash = sha256(normalizedKey);
        var existing = submissions.findExisting(clientHash, idempotencyHash);
        if (existing.isPresent()) return existing.orElseThrow();

        var service = services.findById(request.serviceId())
                .filter(candidate -> candidate.getArchivedAt() == null)
                .orElseThrow(() -> new DemoScenarioNotFoundException("Unknown or archived demo service"));
        var template = templates.findFirstByScenarioTypeAndEnabledTrueAndArchivedAtIsNull(request.symptom())
                .orElseThrow(() -> new DemoScenarioNotFoundException("Unsupported demo symptom"));
        String title = service.getName() + " · " + symptomLabel(request.symptom());
        String summary = impactLabel(request.customerImpact()) + " with "
                + request.signalIntensity().name().toLowerCase(Locale.ROOT) + " signals. "
                + (request.deploymentContext() == DemoInvestigationRequest.DeploymentContext.RECENT_CHANGE
                ? "A recent release is available for correlation." : "No recent deployment is present.");
        var configuration = new DemoInvestigationConfiguration(template, service, request.symptom(),
                request.severity(), request.signalIntensity(), request.customerImpact(),
                request.deploymentContext(), title, summary);
        return submit(configuration, clientHash, idempotencyHash);
    }

    private DemoLiveSubmissionView submit(DemoInvestigationConfiguration configuration,
                                          String clientHash, String idempotencyHash) {
        var existing = submissions.findExisting(clientHash, idempotencyHash);
        if (existing.isPresent()) return existing.orElseThrow();
        limiter.acquire(clientHash);

        UUID publicId = UUID.randomUUID();
        Instant firedAt = clock.instant();
        AlertPayload payload = payload(configuration, publicId, firedAt);
        String fingerprint = fingerprinter.fingerprint(payload);
        try {
            submissions.insert(publicId, configuration, fingerprint, clientHash, idempotencyHash, firedAt);
        } catch (DataIntegrityViolationException race) {
            limiter.release();
            return submissions.findExisting(clientHash, idempotencyHash).orElseThrow(() -> race);
        }

        try {
            evidence.seed(publicId, configuration, firedAt);
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

    private static AlertPayload payload(DemoInvestigationConfiguration configuration, UUID publicId, Instant firedAt) {
        String alertName;
        String summary;
        switch (configuration.symptom()) {
            case BAD_DEPLOY -> {
                alertName = "HighErrorRateAfterDeploy";
                summary = configuration.deploymentContext() == DemoInvestigationRequest.DeploymentContext.RECENT_CHANGE
                        ? "Error rate and p99 latency rose immediately after a synthetic release."
                        : "Error rate and p99 latency rose without a recent release in the evidence window.";
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
        summary = summary + " Customer impact: " + impactLabel(configuration.customerImpact())
                + " Signal intensity: " + configuration.signalIntensity().name().toLowerCase(Locale.ROOT) + ".";
        return new AlertPayload(configuration.service().getName(), alertName, configuration.severity(),
                firedAt, summary, Map.of("environment", "portfolio-sandbox",
                "scenario", configuration.symptom().name(), "demo_run_id", publicId.toString()));
    }

    private static DemoInvestigationOptions.Choice choice(String value, String label, String description) {
        return new DemoInvestigationOptions.Choice(value, label, description);
    }

    private static String symptomLabel(ScenarioTemplate.ScenarioType symptom) {
        return switch (symptom) {
            case BAD_DEPLOY -> "release regression";
            case DEPENDENCY_TIMEOUT -> "dependency timeout";
            case CAPACITY_SATURATION -> "capacity saturation";
            case CACHE_STALENESS -> "stale data";
        };
    }

    private static String impactLabel(DemoInvestigationRequest.CustomerImpact impact) {
        return switch (impact) {
            case DEGRADED -> "Users experience degraded performance";
            case PARTIAL_OUTAGE -> "A subset of user requests is failing";
            case FULL_OUTAGE -> "Most users cannot access the service";
            case STALE_RESULTS -> "Users receive stale application data";
        };
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
