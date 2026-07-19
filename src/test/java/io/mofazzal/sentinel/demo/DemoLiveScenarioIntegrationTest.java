package io.mofazzal.sentinel.demo;

import io.mofazzal.sentinel.agent.application.IncidentRouter;
import io.mofazzal.sentinel.agent.application.ProposalEvaluator;
import io.mofazzal.sentinel.agent.application.ProposalGenerator;
import io.mofazzal.sentinel.agent.domain.Classification;
import io.mofazzal.sentinel.agent.domain.EvidenceSignal;
import io.mofazzal.sentinel.agent.domain.IncidentType;
import io.mofazzal.sentinel.agent.domain.ProposalEvaluation;
import io.mofazzal.sentinel.agent.domain.RemediationProposal;
import io.mofazzal.sentinel.agent.retrieval.RunbookEmbeddingIndexer;
import io.mofazzal.sentinel.agent.retrieval.TextEmbeddingGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.UUID;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.http.MediaType.APPLICATION_JSON;

@Testcontainers
@Import(DemoLiveScenarioIntegrationTest.LiveAgentConfiguration.class)
@SpringBootTest(properties = {
        "spring.profiles.active=seed,demo",
        "sentinel.agent.enabled=true",
        "sentinel.agent.retrieval-mode=semantic",
        "sentinel.remediation.dry-run=true",
        "sentinel.demo.sandbox.per-client-per-minute=1",
        "sentinel.demo.sandbox.global-concurrency=1",
        "sentinel.demo.sandbox.daily-accepted=1",
        "sentinel.demo.sandbox.lease-timeout=30s"
})
class DemoLiveScenarioIntegrationTest {

    private static final String TEST_SECRET =
            "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";
    private static final UUID BAD_DEPLOY = UUID.fromString("51000000-0000-0000-0000-000000000001");

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            "pgvector/pgvector:0.8.2-pg17-bookworm");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(
            DockerImageName.parse("redis:7.4.9-alpine")).withExposedPorts(6379);

    @Container
    static final RabbitMQContainer RABBIT = new RabbitMQContainer(
            DockerImageName.parse("rabbitmq:4.3.2-management-alpine"));

    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.rabbitmq.host", RABBIT::getHost);
        registry.add("spring.rabbitmq.port", () -> RABBIT.getMappedPort(5672));
        registry.add("spring.rabbitmq.username", () -> "guest");
        registry.add("spring.rabbitmq.password", () -> "guest");
        registry.add("sentinel.messaging.retry-delay", () -> "100ms");
        registry.add("sentinel.security.jwt-secret", () -> TEST_SECRET);
        registry.add("sentinel.security.webhook-secret", () -> TEST_SECRET);
    }

    @Autowired
    private WebApplicationContext webContext;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private RunbookEmbeddingIndexer embeddingIndexer;

    private MockMvc mockMvc;

    @BeforeEach
    void mockMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webContext).apply(springSecurity()).build();
        embeddingIndexer.indexAll();
    }

    @Test
    void publicConfiguredInvestigationFlowsThroughBrokerWithEvidenceIdempotencyAndLimits() throws Exception {
        mockMvc.perform(get("/api/v1/demo/scenarios"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].id").isNotEmpty());

        mockMvc.perform(get("/api/v1/demo/investigation-options"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.services.length()").value(12))
                .andExpect(jsonPath("$.symptoms.length()").value(4))
                .andExpect(jsonPath("$.evidencePlan.metricSeries").value(5))
                .andExpect(jsonPath("$.evidencePlan.executionMode").value("DRY_RUN"));

        String request = """
                {"serviceId":"20000000-0000-0000-0000-000000000001",
                 "symptom":"BAD_DEPLOY","severity":"SEV1","signalIntensity":"CRITICAL",
                 "customerImpact":"FULL_OUTAGE","deploymentContext":"RECENT_CHANGE"}
                """;

        String first = mockMvc.perform(post("/api/v1/demo/investigations")
                        .contentType(APPLICATION_JSON).content(request)
                        .header("Idempotency-Key", "browser-run-1")
                        .header("X-Forwarded-For", "198.51.100.10"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.state").value("QUEUED"))
                .andExpect(jsonPath("$.publicId").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        String publicId = com.jayway.jsonpath.JsonPath.read(first, "$.publicId");

        String replay = mockMvc.perform(post("/api/v1/demo/investigations")
                        .contentType(APPLICATION_JSON).content(request)
                        .header("Idempotency-Key", "browser-run-1")
                        .header("X-Forwarded-For", "198.51.100.10"))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        assertThat(com.jayway.jsonpath.JsonPath.<String>read(replay, "$.publicId")).isEqualTo(publicId);

        await(Duration.ofSeconds(15), () -> "COMPLETED".equals(jdbc.queryForObject(
                "select state from demo_live_submission where public_id = ?::uuid",
                String.class, publicId)));

        mockMvc.perform(get("/api/v1/demo/submissions/{id}", publicId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("COMPLETED"))
                .andExpect(jsonPath("$.incidentStatus").value("ESCALATED"));
        mockMvc.perform(get("/api/v1/demo/runs/{id}", publicId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("LIVE"))
                .andExpect(jsonPath("$.scenarioKey").value("custom-bad_deploy"))
                .andExpect(jsonPath("$.timeline.length()").value(5))
                .andExpect(jsonPath("$.remediation.status").value("DRY_RUN"))
                .andExpect(jsonPath("$.evidence.metrics.length()").value(5))
                .andExpect(jsonPath("$.evidence.logs.length()").value(8))
                .andExpect(jsonPath("$.evidence.deployments.length()").value(1))
                .andExpect(jsonPath("$.ledger[0].eventType").value("DRY_RUN"));

        assertThat(jdbc.queryForMap("""
                select scenario_type, severity, signal_intensity, customer_impact, deployment_context
                from demo_live_submission where public_id = ?::uuid
                """, publicId)).containsEntry("scenario_type", "BAD_DEPLOY")
                .containsEntry("severity", "SEV1")
                .containsEntry("signal_intensity", "CRITICAL")
                .containsEntry("customer_impact", "FULL_OUTAGE")
                .containsEntry("deployment_context", "RECENT_CHANGE");

        assertThat(jdbc.queryForObject("""
                select count(*) from incident incident
                join demo_run run on run.incident_id = incident.id
                where run.public_id = ?::uuid
                """, Integer.class, publicId)).isOne();
        assertThat(jdbc.queryForObject("""
                select count(*) from action_claim claim
                join incident on incident.fingerprint = claim.fingerprint
                join demo_run run on run.incident_id = incident.id
                where run.public_id = ?::uuid
                """, Integer.class, publicId)).isZero();
        assertThat(jdbc.queryForObject("""
                select count(*) from metric_sample metric
                join demo_live_submission submission on submission.public_id = ?::uuid
                where metric.service_id = submission.service_id
                  and metric.recorded_at between submission.submitted_at - interval '12 minutes'
                                             and submission.submitted_at
                """, Integer.class, publicId)).isGreaterThanOrEqualTo(60);

        mockMvc.perform(post("/api/v1/demo/investigations")
                        .contentType(APPLICATION_JSON).content(request)
                        .header("Idempotency-Key", "another-client-run")
                        .header("X-Forwarded-For", "198.51.100.11"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("DAILY_LIMIT"));

        mockMvc.perform(get("/api/v1/catalog/services"))
                .andExpect(status().isUnauthorized());
    }

    private static void await(Duration timeout, BooleanSupplier condition) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) return;
            try {
                Thread.sleep(50);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while awaiting live scenario", exception);
            }
        }
        throw new AssertionError("Condition was not met within " + timeout);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class LiveAgentConfiguration {
        @Bean
        IncidentRouter liveRouter() {
            return request -> new Classification(IncidentType.BAD_DEPLOY,
                    List.of(EvidenceSignal.DEPLOYMENTS, EvidenceSignal.METRICS,
                            EvidenceSignal.LOGS, EvidenceSignal.RUNBOOKS),
                    "Fixed live scenario routes to bounded bad-deploy investigation");
        }

        @Bean
        ProposalGenerator liveGenerator() {
            return (request, classification, evidence, feedback) -> {
                var runbook = evidence.runbooks().getFirst();
                return new RemediationProposal(runbook.actionType(), runbook.title(),
                        List.of("Confirm release correlation", "Propose one guarded rollback"),
                        "Fresh telemetry and release history correlate", "The deterministic gate remains authoritative");
            };
        }

        @Bean
        ProposalEvaluator liveEvaluator() {
            return (request, evidence, proposal) -> new ProposalEvaluation(true, "Grounded and bounded");
        }

        @Bean
        TextEmbeddingGateway liveEmbeddingGateway() {
            return new TextEmbeddingGateway() {
                @Override
                public float[] embed(String text) {
                    String normalized = text.toLowerCase(Locale.ROOT);
                    int axis = normalized.contains("rollback") || normalized.contains("release")
                            || normalized.contains("deployment") || normalized.contains("error rate") ? 0 : 7;
                    float[] vector = new float[768];
                    vector[axis] = 1.0f;
                    return vector;
                }

                @Override
                public int dimensions() {
                    return 768;
                }
            };
        }
    }
}
