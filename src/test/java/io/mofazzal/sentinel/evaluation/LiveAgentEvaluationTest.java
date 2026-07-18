package io.mofazzal.sentinel.evaluation;

import io.mofazzal.sentinel.agent.application.IncidentRouter;
import io.mofazzal.sentinel.agent.application.ProposalEvaluator;
import io.mofazzal.sentinel.agent.application.ProposalGenerator;
import io.mofazzal.sentinel.agent.application.TranscriptRecorder;
import io.mofazzal.sentinel.agent.application.TriageWorkflow;
import io.mofazzal.sentinel.agent.domain.Classification;
import io.mofazzal.sentinel.agent.domain.EvidenceBundle;
import io.mofazzal.sentinel.agent.domain.EvidenceSignal;
import io.mofazzal.sentinel.agent.domain.TriageOutcome;
import io.mofazzal.sentinel.agent.domain.TriageRequest;
import io.mofazzal.sentinel.agent.retrieval.RunbookEmbeddingIndexer;
import io.mofazzal.sentinel.agent.retrieval.RunbookSearchEngine;
import io.mofazzal.sentinel.tools.RunbookRetrieveTool.RunbookSummary;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("live-evaluation")
@Testcontainers
@SpringBootTest(properties = {
        "spring.profiles.active=seed",
        "spring.rabbitmq.listener.simple.auto-startup=false",
        "spring.ai.model.chat=ollama",
        "spring.ai.model.embedding=ollama",
        "spring.ai.ollama.chat.model=qwen3:4b",
        "spring.ai.ollama.chat.think=false",
        "spring.ai.ollama.chat.num-predict=256",
        "spring.ai.ollama.chat.keep-alive=30m",
        "spring.ai.ollama.embedding.model=nomic-embed-text",
        "spring.ai.ollama.embedding.keep-alive=30m",
        "sentinel.agent.enabled=false",
        "sentinel.agent.retrieval-mode=semantic",
        "sentinel.security.jwt-secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "sentinel.security.webhook-secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
})
class LiveAgentEvaluationTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            "pgvector/pgvector:0.8.2-pg17-bookworm");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(
            DockerImageName.parse("redis:7.4.9-alpine")).withExposedPorts(6379);

    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired
    private IncidentRouter router;

    @Autowired
    private ProposalGenerator generator;

    @Autowired
    private ProposalEvaluator evaluator;

    @Autowired
    private RunbookSearchEngine runbooks;

    @Autowired
    private RunbookEmbeddingIndexer indexer;

    @Test
    void scoresSelectedGroundTruthSplitThroughRealAdapters() throws Exception {
        EvaluationScenario.Split split = EvaluationScenario.Split.valueOf(
                System.getenv().getOrDefault("SENTINEL_EVAL_SPLIT", "DEVELOPMENT")
                        .trim().toUpperCase(Locale.ROOT));
        EvaluationMode mode = EvaluationMode.valueOf(
                System.getenv().getOrDefault("SENTINEL_EVAL_MODE", "ROUTING_RETRIEVAL")
                        .trim().toUpperCase(Locale.ROOT));
        List<String> selectedIds = selectedScenarioIds();
        List<EvaluationScenario> truth = GroundTruthCorpusTest.loadCorpus().stream()
                .filter(scenario -> scenario.split() == split)
                .filter(scenario -> selectedIds.isEmpty() || selectedIds.contains(scenario.id()))
                .toList();
        indexer.indexAll();
        int maxAttempts = Integer.parseInt(
                System.getenv().getOrDefault("SENTINEL_EVAL_MAX_ATTEMPTS", "1"));

        List<EvaluationPrediction> predictions = new ArrayList<>();
        List<Map<String, Object>> scenarioReports = new ArrayList<>();
        for (EvaluationScenario scenario : truth) {
            predictions.add(evaluate(scenario, scenarioReports, maxAttempts, mode));
        }
        var score = new AgentEvaluationScorer().score(truth, predictions);
        Map<String, Object> report = report(split, score, scenarioReports, maxAttempts, mode);
        Path reportPath = Path.of("build", "reports", "evaluation",
                "qwen3-4b-" + split.name().toLowerCase(Locale.ROOT) + "-"
                        + mode.name().toLowerCase(Locale.ROOT) + ".json");
        Files.createDirectories(reportPath.getParent());
        JsonMapper.builder().build().writerWithDefaultPrettyPrinter().writeValue(reportPath.toFile(), report);

        System.out.println("SENTINEL_LIVE_EVALUATION="
                + JsonMapper.builder().build().writeValueAsString(report));
        if (mode == EvaluationMode.FULL) {
            assertThat(score.hallucinations()).as("grounding violations").isZero();
        }
    }

    private EvaluationPrediction evaluate(EvaluationScenario scenario,
                                          List<Map<String, Object>> reports,
                                          int maxAttempts,
                                          EvaluationMode mode) {
        UUID incidentId = UUID.randomUUID();
        TriageRequest request = new TriageRequest(incidentId, scenario.service(), scenario.summary(),
                Instant.parse("2026-07-19T00:00:00Z"));
        AtomicReference<Classification> classification = new AtomicReference<>();
        AtomicReference<List<RunbookSummary>> retrieved = new AtomicReference<>(List.of());
        RoleTiming timing = new RoleTiming();
        if (mode == EvaluationMode.ROUTING_RETRIEVAL) {
            return evaluateRoutingAndRetrieval(scenario, request, classification, retrieved, timing, reports);
        }
        TriageWorkflow workflow = new TriageWorkflow(
                triageRequest -> timing.time("classification", () -> {
                    Classification result = router.classify(triageRequest);
                    classification.set(result);
                    return result;
                }),
                (triageRequest, result) -> timing.time("retrieval", () -> {
                    List<RunbookSummary> matches = result.relevantSignals().contains(EvidenceSignal.RUNBOOKS)
                            ? runbooks.search(triageRequest.summary(), 3).stream()
                            .map(match -> new RunbookSummary(match.id(), match.title(),
                                    match.symptomDescription(), match.steps(), match.actionType(),
                                    match.similarity()))
                            .toList()
                            : List.of();
                    retrieved.set(matches);
                    return new EvidenceBundle(List.of(), List.of(), List.of(), matches);
                }),
                (triageRequest, result, evidence, feedback) -> timing.time("generation",
                        () -> generator.propose(triageRequest, result, evidence, feedback)),
                (triageRequest, evidence, proposal) -> timing.time("evaluation",
                        () -> evaluator.evaluate(triageRequest, evidence, proposal)),
                (ignoredIncident, ignoredType, ignoredIteration, ignoredContent) -> { },
                maxAttempts);

        long started = System.nanoTime();
        TriageOutcome outcome = null;
        String error = null;
        try {
            outcome = workflow.triage(request);
        } catch (RuntimeException failure) {
            error = failure.getClass().getSimpleName() + ": " + bounded(failure.getMessage());
        }
        long totalMillis = Duration.ofNanos(System.nanoTime() - started).toMillis();
        Classification actualClassification = classification.get();
        TriageOutcome finalOutcome = outcome;
        EvaluationPrediction prediction = new EvaluationPrediction(
                actualClassification == null ? null : actualClassification.type(),
                actualClassification == null ? List.of() : actualClassification.relevantSignals(),
                retrieved.get().stream().map(RunbookSummary::title).toList(),
                finalOutcome == null ? null : finalOutcome.optionalProposal()
                        .map(proposal -> proposal.runbookTitle()).orElse(null),
                finalOutcome == null ? null : finalOutcome.optionalProposal()
                        .map(proposal -> proposal.actionType()).orElse(null),
                finalOutcome == null || finalOutcome.decision() == TriageOutcome.Decision.ESCALATED);

        Map<String, Object> scenarioReport = new LinkedHashMap<>();
        scenarioReport.put("id", scenario.id());
        scenarioReport.put("expectedType", scenario.expectedType());
        scenarioReport.put("actualType", prediction.type());
        scenarioReport.put("requiredSignals", scenario.requiredSignals());
        scenarioReport.put("actualSignals", prediction.signals());
        scenarioReport.put("expectedRunbook", scenario.expectedRunbookTitle());
        scenarioReport.put("retrievedRunbooks", prediction.retrievedRunbookTitles());
        scenarioReport.put("retrievalSimilarities", retrievalSimilarities(retrieved.get()));
        scenarioReport.put("proposedRunbook", prediction.proposedRunbookTitle());
        scenarioReport.put("expectedEscalation", scenario.expectedEscalation());
        scenarioReport.put("actualEscalation", prediction.escalated());
        scenarioReport.put("totalMillis", totalMillis);
        scenarioReport.put("roleMillis", timing.millis());
        scenarioReport.put("error", error);
        reports.add(scenarioReport);
        return prediction;
    }

    private Map<String, Object> report(EvaluationScenario.Split split,
                                       AgentEvaluationScorer.EvaluationScore score,
                                       List<Map<String, Object>> scenarios,
                                       int maxAttempts,
                                       EvaluationMode mode) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("model", "qwen3:4b");
        report.put("embeddingModel", "nomic-embed-text");
        report.put("split", split);
        report.put("mode", mode);
        report.put("maxAttempts", maxAttempts);
        report.put("scenarioCount", score.scenarios());
        report.put("classificationAccuracy", score.classificationAccuracy());
        report.put("requiredSignalCoverage", score.requiredSignalCoverage());
        report.put("retrievalRecallAt3", score.retrievalRecall());
        report.put("retrievalGroundTruthMatch", score.retrievalGroundTruthMatch());
        report.put("outcomeAccuracy", mode == EvaluationMode.FULL ? score.outcomeAccuracy() : "NOT_RUN");
        report.put("groundingViolations", mode == EvaluationMode.FULL ? score.hallucinations() : "NOT_RUN");
        report.put("scenarios", scenarios);
        return report;
    }

    private EvaluationPrediction evaluateRoutingAndRetrieval(
            EvaluationScenario scenario,
            TriageRequest request,
            AtomicReference<Classification> classification,
            AtomicReference<List<RunbookSummary>> retrieved,
            RoleTiming timing,
            List<Map<String, Object>> reports) {
        long started = System.nanoTime();
        String error = null;
        try {
            Classification result = timing.time("classification", () -> router.classify(request));
            classification.set(result);
            if (result.relevantSignals().contains(EvidenceSignal.RUNBOOKS)) {
                retrieved.set(timing.time("retrieval", () -> runbooks.search(request.summary(), 3).stream()
                        .map(match -> new RunbookSummary(match.id(), match.title(),
                                match.symptomDescription(), match.steps(), match.actionType(),
                                match.similarity()))
                        .toList()));
            }
        } catch (RuntimeException failure) {
            error = failure.getClass().getSimpleName() + ": " + bounded(failure.getMessage());
        }
        EvaluationPrediction prediction = new EvaluationPrediction(
                classification.get() == null ? null : classification.get().type(),
                classification.get() == null ? List.of() : classification.get().relevantSignals(),
                retrieved.get().stream().map(RunbookSummary::title).toList(),
                null, null, retrieved.get().isEmpty());
        Map<String, Object> scenarioReport = new LinkedHashMap<>();
        scenarioReport.put("id", scenario.id());
        scenarioReport.put("expectedType", scenario.expectedType());
        scenarioReport.put("actualType", prediction.type());
        scenarioReport.put("requiredSignals", scenario.requiredSignals());
        scenarioReport.put("actualSignals", prediction.signals());
        scenarioReport.put("expectedRunbook", scenario.expectedRunbookTitle());
        scenarioReport.put("retrievedRunbooks", prediction.retrievedRunbookTitles());
        scenarioReport.put("retrievalSimilarities", retrievalSimilarities(retrieved.get()));
        scenarioReport.put("totalMillis", Duration.ofNanos(System.nanoTime() - started).toMillis());
        scenarioReport.put("roleMillis", timing.millis());
        scenarioReport.put("error", error);
        reports.add(scenarioReport);
        return prediction;
    }

    private List<String> selectedScenarioIds() {
        String configured = System.getenv().getOrDefault("SENTINEL_EVAL_SCENARIOS", "").trim();
        return configured.isEmpty() ? List.of() : List.of(configured.split(",")).stream()
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
    }

    private List<Map<String, Object>> retrievalSimilarities(List<RunbookSummary> matches) {
        return matches.stream().map(match -> {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("title", match.title());
            value.put("similarity", match.similarity());
            return value;
        }).toList();
    }

    private static String bounded(String message) {
        if (message == null) {
            return "no message";
        }
        return message.substring(0, Math.min(300, message.length()));
    }

    private static final class RoleTiming {
        private final Map<String, AtomicLong> nanos = new LinkedHashMap<>();

        <T> T time(String role, java.util.function.Supplier<T> work) {
            long started = System.nanoTime();
            try {
                return work.get();
            } finally {
                nanos.computeIfAbsent(role, ignored -> new AtomicLong())
                        .addAndGet(System.nanoTime() - started);
            }
        }

        Map<String, Long> millis() {
            Map<String, Long> result = new LinkedHashMap<>();
            nanos.forEach((role, duration) -> result.put(role,
                    Duration.ofNanos(duration.get()).toMillis()));
            return result;
        }
    }

    private enum EvaluationMode {
        ROUTING_RETRIEVAL,
        FULL
    }
}
