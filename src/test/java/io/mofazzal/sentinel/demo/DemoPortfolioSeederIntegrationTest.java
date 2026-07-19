package io.mofazzal.sentinel.demo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@ActiveProfiles("demo")
@SpringBootTest(properties = {
        "spring.rabbitmq.listener.simple.auto-startup=false",
        "sentinel.security.jwt-secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "sentinel.security.webhook-secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
})
class DemoPortfolioSeederIntegrationTest {

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
    private DemoPortfolioSeeder seeder;

    @Autowired
    private DemoOperationsDigitalTwinSeeder digitalTwinSeeder;

    @Autowired
    private JdbcTemplate jdbc;

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
    void seedsASubstantialTraceableDigitalTwinAndRemainsIdempotent() throws Exception {
        ApplicationArguments noArguments = new DefaultApplicationArguments(new String[0]);
        seeder.run(noArguments);
        digitalTwinSeeder.run(noArguments);

        assertThat(jdbc.queryForObject("select count(*) from team", Integer.class)).isEqualTo(4);
        assertThat(jdbc.queryForObject("select count(*) from fleet_service", Integer.class)).isEqualTo(12);
        assertThat(jdbc.queryForObject("select count(*) from service_dependency", Integer.class)).isEqualTo(18);
        assertThat(jdbc.queryForObject("select count(*) from deployment", Integer.class)).isGreaterThanOrEqualTo(60);
        assertThat(jdbc.queryForObject("select count(*) from runbook", Integer.class)).isEqualTo(10);
        assertThat(jdbc.queryForObject("select count(*) from metric_sample", Integer.class)).isEqualTo(10_800);
        assertThat(jdbc.queryForObject("select count(*) from log_event", Integer.class)).isEqualTo(1_080);
        assertThat(jdbc.queryForObject("select count(*) from incident", Integer.class)).isEqualTo(30);
        assertThat(jdbc.queryForObject("select count(*) from demo_run", Integer.class)).isEqualTo(30);
        assertThat(jdbc.queryForList("""
                select status from incident
                where id in (?, ?, ?)
                order by created_at
                """, String.class,
                DemoPortfolioSeeder.GROUNDED_INCIDENT,
                DemoPortfolioSeeder.AMBIGUOUS_INCIDENT,
                DemoPortfolioSeeder.APPROVAL_INCIDENT)).containsExactly(
                "ESCALATED", "ESCALATED", "AWAITING_APPROVAL");
        assertThat(jdbc.queryForObject("""
                select count(*)
                from demo_run demo
                join incident on incident.id = demo.incident_id
                join agent_run run on run.incident_id = incident.id
                join agent_transcript_entry entry on entry.run_id = run.id
                """, Integer.class)).isEqualTo(149);
        assertThat(jdbc.queryForObject("select count(*) from action_ledger", Integer.class)).isEqualTo(54);
        assertThat(jdbc.queryForList("select distinct event_type from action_ledger", String.class))
                .contains("DRY_RUN", "APPROVAL_REQUESTED", "IN_PROGRESS", "APPLIED", "COMPENSATED");
        assertThat(jdbc.queryForObject("select count(*) from action_claim", Integer.class)).isEqualTo(10);
        assertThat(jdbc.queryForObject("select count(*) from demo_dataset_version", Integer.class)).isOne();
        assertThat(jdbc.queryForObject("""
                select count(*) from incident
                where fingerprint like 'demo:v2:%'
                  and correlated_deployment_id is not null
                  and (select deployed_at from deployment where id = correlated_deployment_id) > created_at
                """, Integer.class)).isZero();
        assertThat(jdbc.queryForObject("""
                select count(*) from incident
                where fingerprint like 'demo:v2:%'
                  and (select count(distinct metric_name) from metric_sample
                       where service_id = incident.service_id
                         and recorded_at between incident.created_at - interval '5 minutes'
                                             and incident.created_at + interval '5 minutes') < 5
                """, Integer.class)).isZero();
        assertThat(jdbc.queryForObject("""
                select count(*) from incident
                where fingerprint like 'demo:v2:%'
                  and not exists (select 1 from log_event
                                  where service_id = incident.service_id
                                    and occurred_at between incident.created_at - interval '5 minutes'
                                                        and incident.created_at + interval '5 minutes')
                """, Integer.class)).isZero();
    }

    @Test
    void recordedLedgerStillUsesTheAppendOnlyDatabaseBoundary() {
        assertThatThrownBy(() -> jdbc.update("""
                update action_ledger set details = 'rewritten'
                where incident_id = ?
                """, DemoPortfolioSeeder.GROUNDED_INCIDENT))
                .rootCause()
                .hasMessageContaining("action_ledger is append-only");
    }

    @Test
    void exposesOnlyCuratedDemoViewsWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/demo/runs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(30))
                .andExpect(jsonPath("$[0].scenarioKey").value("capacity-approval"))
                .andExpect(jsonPath("$[0].summary").isNotEmpty());

        mockMvc.perform(get("/api/v1/demo/runs/45000000-0000-0000-0000-000000000001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.service").value("payments-api"))
                .andExpect(jsonPath("$.timeline.length()").value(6))
                .andExpect(jsonPath("$.remediation.action").value("ROLLBACK_DEPLOYMENT"))
                .andExpect(jsonPath("$.remediation.status").value("DRY_RUN"))
                .andExpect(jsonPath("$.ledger[0].eventType").value("DRY_RUN"))
                .andExpect(jsonPath("$.disclaimer").value(DemoRunQueryService.DISCLAIMER));

        mockMvc.perform(get("/api/v1/demo/runs/45000000-0000-0000-0000-000000000099"))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/incidents"))
                .andExpect(status().isUnauthorized());
    }
}
