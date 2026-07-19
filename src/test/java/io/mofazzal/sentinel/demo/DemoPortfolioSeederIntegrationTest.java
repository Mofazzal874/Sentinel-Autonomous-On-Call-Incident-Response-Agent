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
    void seedsThreeTraceableStoriesAndRemainsIdempotent() throws Exception {
        ApplicationArguments noArguments = new DefaultApplicationArguments(new String[0]);
        seeder.run(noArguments);

        assertThat(jdbc.queryForObject("select count(*) from demo_run", Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForList("""
                select scenario_key from demo_run order by started_at
                """, String.class)).containsExactly(
                "faulty-deployment", "ambiguous-dependency", "capacity-approval");
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
                """, Integer.class)).isEqualTo(14);
        assertThat(jdbc.queryForList("""
                select event_type from action_ledger order by recorded_at
                """, String.class)).containsExactly("DRY_RUN", "APPROVAL_REQUESTED");
        assertThat(jdbc.queryForObject("select count(*) from action_claim", Integer.class)).isZero();
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
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].scenarioKey").value("capacity-approval"));

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
