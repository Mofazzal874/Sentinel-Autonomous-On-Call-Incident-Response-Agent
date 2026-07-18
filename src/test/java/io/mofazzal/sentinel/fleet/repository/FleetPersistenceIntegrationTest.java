package io.mofazzal.sentinel.fleet.repository;

import io.mofazzal.sentinel.alert.api.AlertPayload;
import io.mofazzal.sentinel.alert.messaging.TriageCommand;
import io.mofazzal.sentinel.fleet.domain.Deployment;
import io.mofazzal.sentinel.fleet.domain.DeploymentStatus;
import io.mofazzal.sentinel.fleet.domain.FleetService;
import io.mofazzal.sentinel.fleet.domain.LogEvent;
import io.mofazzal.sentinel.fleet.domain.LogLevel;
import io.mofazzal.sentinel.fleet.domain.MetricSample;
import io.mofazzal.sentinel.incident.application.IncidentCreationService;
import io.mofazzal.sentinel.incident.domain.IncidentSeverity;
import io.mofazzal.sentinel.incident.domain.IncidentStatus;
import io.mofazzal.sentinel.incident.repository.IncidentRepository;
import io.mofazzal.sentinel.tools.DeployQueryTool;
import io.mofazzal.sentinel.tools.LogSearchTool;
import io.mofazzal.sentinel.tools.MetricsQueryTool;
import io.mofazzal.sentinel.tools.RunbookRetrieveTool;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Base64;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest(properties = {
        "spring.rabbitmq.listener.simple.auto-startup=false",
        "sentinel.security.jwt-secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "sentinel.security.webhook-secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
})
class FleetPersistenceIntegrationTest {

    private static final String TEST_JWT_SECRET =
            "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            "pgvector/pgvector:0.8.2-pg17-bookworm"
    );

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private FleetServiceRepository serviceRepository;

    @Autowired
    private DeploymentRepository deploymentRepository;

    @Autowired
    private MetricSampleRepository metricRepository;

    @Autowired
    private LogEventRepository logRepository;

    @Autowired
    private IncidentCreationService incidentCreationService;

    @Autowired
    private IncidentRepository incidentRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DeployQueryTool deployTool;

    @Autowired
    private MetricsQueryTool metricsTool;

    @Autowired
    private LogSearchTool logTool;

    @Autowired
    private RunbookRetrieveTool runbookTool;

    @Autowired
    private WebApplicationContext webContext;

    private MockMvc mockMvc;

    @BeforeEach
    void configureMockMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webContext)
                .apply(springSecurity())
                .build();
    }

    @Test
    void jwtRoleMatrixIsStatelessAndKeepsAgentUnderPrivileged() throws Exception {
        mockMvc.perform(get("/api/v1/fleet/services"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/fleet/services")
                        .header("Authorization", "Bearer " + signedToken("VIEWER")))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Set-Cookie"));

        mockMvc.perform(get("/api/v1/fleet/services")
                        .with(jwt().authorities(() -> "ROLE_AGENT")))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/incidents/00000000-0000-0000-0000-000000000001/approve")
                        .with(jwt().authorities(() -> "ROLE_AGENT")))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/incidents/00000000-0000-0000-0000-000000000001/approve")
                        .with(jwt().authorities(() -> "ROLE_SRE_APPROVER")))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/admin/security-check")
                        .with(jwt().authorities(() -> "ROLE_AGENT")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/admin/security-check")
                        .with(jwt().authorities(() -> "ROLE_ADMIN")))
                .andExpect(status().isNotFound());
    }

    private static String signedToken(String role) {
        SecretKey key = new SecretKeySpec(Base64.getDecoder().decode(TEST_JWT_SECRET), "HmacSHA256");
        NimbusJwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("sentinel-local")
                .subject("integration-viewer")
                .audience(List.of("sentinel-api"))
                .issuedAt(now.minusSeconds(1))
                .expiresAt(now.plusSeconds(60))
                .claim("roles", List.of(role))
                .build();
        return encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    @Test
    @Transactional
    @WithMockUser(username = "sentinel-agent", roles = "AGENT")
    void agentToolsCorrelateSeededScenarioThroughRealPostgresql() {
        FleetService payments = serviceRepository.findByName("payments-api").orElseThrow();
        Instant deploymentAt = Instant.parse("2026-07-18T10:00:00Z");
        Instant incidentAt = deploymentAt.plus(5, ChronoUnit.MINUTES);
        deploymentRepository.saveAndFlush(new Deployment(
                payments, "2026.07.18.9", "phase3-bad-sha", deploymentAt,
                "release-bot", DeploymentStatus.SUCCEEDED));

        List<MetricSample> samples = new java.util.ArrayList<>();
        for (int minute = -10; minute < 10; minute++) {
            samples.add(new MetricSample(payments, "error_rate",
                    minute < 0 ? new BigDecimal("0.010000") : new BigDecimal("0.180000"),
                    incidentAt.plus(minute, ChronoUnit.MINUTES)));
        }
        metricRepository.saveAllAndFlush(samples);
        logRepository.saveAllAndFlush(List.of(
                new LogEvent(payments, LogLevel.ERROR, "Payment request 101 timed out",
                        incidentAt.minusSeconds(30), "phase3-trace-1"),
                new LogEvent(payments, LogLevel.ERROR, "Payment request 202 timed out",
                        incidentAt, "phase3-trace-2")
        ));

        assertThat(deployTool.recentDeploys("payments-api", incidentAt))
                .first()
                .extracting(DeployQueryTool.DeploySummary::gitSha)
                .isEqualTo("phase3-bad-sha");

        MetricsQueryTool.MetricWindow metricWindow = metricsTool.window(
                "payments-api", "error_rate",
                incidentAt.minus(10, ChronoUnit.MINUTES),
                incidentAt.plus(10, ChronoUnit.MINUTES));
        assertThat(metricWindow.points()).hasSize(20);
        assertThat(metricWindow.percentageDelta()).isEqualByComparingTo("1700.00");

        assertThat(logTool.errorsAround("payments-api", incidentAt, java.time.Duration.ofMinutes(5)))
                .singleElement()
                .satisfies(cluster -> {
                    assertThat(cluster.count()).isEqualTo(2);
                    assertThat(cluster.traceIds()).containsExactly("phase3-trace-2", "phase3-trace-1");
                });

        assertThat(runbookTool.search("faulty service deployment"))
                .singleElement()
                .satisfies(runbook -> assertThat(runbook.title()).contains("Rollback"));
    }

    @Test
    void toolMethodSecurityRejectsMissingAuthenticationBeforeRepositoryAccess() {
        assertThatThrownBy(() -> deployTool.recentDeploys("payments-api", Instant.now()))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }

    @Test
    @Transactional
    void migrationsAndCorrelationQueryUseRealPostgreSql() {
        String vectorVersion = jdbcTemplate.queryForObject(
                "select extversion from pg_extension where extname = 'vector'", String.class);
        assertThat(vectorVersion).isEqualTo("0.8.2");

        FleetService payments = serviceRepository.findByName("payments-api").orElseThrow();
        Instant spikeAt = Instant.parse("2026-07-15T12:05:00Z");
        Deployment older = new Deployment(payments, "2026.07.14.3", "test-good-sha",
                spikeAt.minus(1, ChronoUnit.DAYS), "test", DeploymentStatus.SUCCEEDED);
        Deployment bad = new Deployment(payments, "2026.07.15.1", "test-bad-sha",
                spikeAt.minus(5, ChronoUnit.MINUTES), "test", DeploymentStatus.SUCCEEDED);
        Deployment afterSpike = new Deployment(payments, "2026.07.15.2", "test-after-sha",
                spikeAt.plus(1, ChronoUnit.MINUTES), "test", DeploymentStatus.SUCCEEDED);
        deploymentRepository.saveAllAndFlush(List.of(older, bad, afterSpike));

        List<Deployment> correlated = deploymentRepository.recentBefore(
                payments.getId(), spikeAt, PageRequest.of(0, 3));

        assertThat(correlated)
                .extracting(Deployment::getGitSha)
                .containsExactly("test-bad-sha", "test-good-sha");

        metricRepository.saveAllAndFlush(List.of(
                new MetricSample(payments, "error_rate", new BigDecimal("0.01"), spikeAt.minusSeconds(60)),
                new MetricSample(payments, "error_rate", new BigDecimal("0.18"), spikeAt),
                new MetricSample(payments, "error_rate", new BigDecimal("0.20"), spikeAt.plusSeconds(60))
        ));
        assertThat(metricRepository.recentWindow(
                payments.getId(), "error_rate", spikeAt.minusSeconds(120), spikeAt.plusSeconds(120),
                PageRequest.of(0, 2)))
                .hasSize(2)
                .extracting(MetricSample::getRecordedAt)
                .containsExactly(spikeAt.plusSeconds(60), spikeAt);

        logRepository.saveAllAndFlush(List.of(
                new LogEvent(payments, LogLevel.WARN, "Timeout threshold exceeded",
                        spikeAt.minusSeconds(30), "test-trace-1"),
                new LogEvent(payments, LogLevel.ERROR, "Payment authorization timed out",
                        spikeAt, "test-trace-1"),
                new LogEvent(payments, LogLevel.ERROR, "Circuit breaker opened",
                        spikeAt.plusSeconds(30), "test-trace-2")
        ));
        assertThat(logRepository.recentWindow(
                payments.getId(), spikeAt.minusSeconds(60), spikeAt.plusSeconds(60),
                PageRequest.of(0, 2)))
                .hasSize(2)
                .extracting(LogEvent::getOccurredAt)
                .containsExactly(spikeAt.plusSeconds(30), spikeAt);
        assertThat(logRepository.searchWindow(
                payments.getId(), "circuit breaker", spikeAt.minusSeconds(60), spikeAt.plusSeconds(60),
                PageRequest.of(0, 5)))
                .singleElement()
                .extracting(LogEvent::getTraceId)
                .isEqualTo("test-trace-2");
    }

    @Test
    void incidentSinkTreatsRepeatedFingerprintAsOneDatabaseEffect() {
        Instant receivedAt = Instant.parse("2026-07-18T00:00:00Z");
        AlertPayload payload = new AlertPayload(
                "payments-api",
                "HighErrorRate",
                IncidentSeverity.SEV2,
                receivedAt.minusSeconds(30),
                "Error rate is above threshold",
                Map.of("environment", "production")
        );
        TriageCommand command = TriageCommand.create("integration-fingerprint", payload, receivedAt);

        assertThat(incidentCreationService.createIfAbsent(command)).isTrue();
        assertThat(incidentCreationService.createIfAbsent(command)).isFalse();

        assertThat(incidentRepository.countByFingerprint(command.fingerprint())).isOne();
        assertThat(incidentRepository.findByFingerprint(command.fingerprint()))
                .get()
                .extracting(incident -> incident.getStatus(), incident -> incident.getSeverity())
                .containsExactly(IncidentStatus.OPEN, IncidentSeverity.SEV2);
    }
}
