package io.mofazzal.sentinel.fleet.api;

import io.mofazzal.sentinel.alert.api.AlertPayload;
import io.mofazzal.sentinel.alert.messaging.TriageCommand;
import io.mofazzal.sentinel.incident.application.IncidentCreationService;
import io.mofazzal.sentinel.incident.application.UnknownFleetServiceException;
import io.mofazzal.sentinel.incident.domain.IncidentSeverity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.UUID;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest(properties = {
        "spring.rabbitmq.listener.simple.auto-startup=false",
        "sentinel.security.jwt-secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "sentinel.security.webhook-secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
})
@Transactional
class CatalogAdministrationIntegrationTest {

    private static final UUID PLATFORM_TEAM = UUID.fromString("10000000-0000-0000-0000-000000000003");
    private static final UUID PAYMENTS_SERVICE = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID CHECKOUT_SERVICE = UUID.fromString("20000000-0000-0000-0000-000000000002");

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            "pgvector/pgvector:0.8.2-pg17-bookworm");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private WebApplicationContext webContext;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private IncidentCreationService incidents;

    private MockMvc mockMvc;

    @BeforeEach
    void configureMockMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webContext)
                .apply(springSecurity())
                .build();
    }

    @Test
    void generatedTeamIdAndOptimisticArchiveLifecycleAreEnforced() throws Exception {
        mockMvc.perform(get("/api/v1/catalog/teams"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/catalog/teams")
                        .with(viewer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Edge Reliability","contactChannel":"#edge-reliability"}
                                """))
                .andExpect(status().isForbidden());

        String created = mockMvc.perform(post("/api/v1/catalog/teams")
                        .with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Edge Reliability","contactChannel":"#edge-reliability"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.version").value(0))
                .andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(com.jayway.jsonpath.JsonPath.read(created, "$.id"));

        mockMvc.perform(put("/api/v1/catalog/teams/{id}", id)
                        .with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Edge Platform","contactChannel":"#edge-platform","version":0}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1));

        mockMvc.perform(put("/api/v1/catalog/teams/{id}", id)
                        .with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Stale Rename","contactChannel":"#stale","version":0}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));

        mockMvc.perform(delete("/api/v1/catalog/teams/{id}", id)
                        .queryParam("version", "1")
                        .with(admin()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/catalog/teams")
                        .with(viewer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.id == '%s')]", id).isEmpty());

        assertThat(jdbc.queryForObject(
                "select archived_at is not null from team where id = ?", Boolean.class, id)).isTrue();
    }

    @Test
    void serviceDependencyAndRunbookFollowDomainSpecificDeleteRules() throws Exception {
        String serviceJson = mockMvc.perform(post("/api/v1/catalog/services")
                        .with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"edge-proxy",
                                  "ownerTeamId":"10000000-0000-0000-0000-000000000003",
                                  "tier":"STANDARD",
                                  "allowedActions":["RESTART_SERVICE","SCALE_OUT"]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.ownerTeamId").value(PLATFORM_TEAM.toString()))
                .andReturn().getResponse().getContentAsString();
        UUID serviceId = UUID.fromString(com.jayway.jsonpath.JsonPath.read(serviceJson, "$.id"));

        String dependencyJson = mockMvc.perform(post("/api/v1/catalog/dependencies")
                        .with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "callerServiceId":"20000000-0000-0000-0000-000000000002",
                                  "dependencyServiceId":"20000000-0000-0000-0000-000000000001",
                                  "criticality":"REQUIRED"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.callerServiceId").value(CHECKOUT_SERVICE.toString()))
                .andExpect(jsonPath("$.dependencyServiceId").value(PAYMENTS_SERVICE.toString()))
                .andReturn().getResponse().getContentAsString();
        UUID dependencyId = UUID.fromString(com.jayway.jsonpath.JsonPath.read(dependencyJson, "$.id"));

        mockMvc.perform(post("/api/v1/catalog/dependencies")
                        .with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "callerServiceId":"20000000-0000-0000-0000-000000000002",
                                  "dependencyServiceId":"20000000-0000-0000-0000-000000000001",
                                  "criticality":"REQUIRED"
                                }
                                """))
                .andExpect(status().isConflict());

        mockMvc.perform(delete("/api/v1/catalog/dependencies/{id}", dependencyId)
                        .queryParam("version", "0")
                        .with(admin()))
                .andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject(
                "select count(*) from service_dependency where id = ?", Integer.class, dependencyId)).isZero();

        String runbookJson = mockMvc.perform(post("/api/v1/catalog/runbooks")
                        .with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title":"Drain one unhealthy edge proxy",
                                  "symptomDescription":"One proxy has sustained connection failures.",
                                  "steps":"Confirm one unhealthy replica; drain it; verify upstream errors.",
                                  "actionType":"RESTART_SERVICE"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.version").value(0))
                .andReturn().getResponse().getContentAsString();
        UUID runbookId = UUID.fromString(com.jayway.jsonpath.JsonPath.read(runbookJson, "$.id"));

        mockMvc.perform(delete("/api/v1/catalog/runbooks/{id}", runbookId)
                        .queryParam("version", "0")
                        .with(admin()))
                .andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject(
                "select archived_at is not null from runbook where id = ?", Boolean.class, runbookId)).isTrue();

        mockMvc.perform(delete("/api/v1/catalog/services/{id}", serviceId)
                        .queryParam("version", "0")
                        .with(admin()))
                .andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject(
                "select archived_at is not null from fleet_service where id = ?", Boolean.class, serviceId)).isTrue();

        Instant now = Instant.parse("2030-01-01T00:00:00Z");
        AlertPayload payload = new AlertPayload("edge-proxy", "ArchivedServiceAlert",
                IncidentSeverity.SEV3, now.minusSeconds(10), "Archived service", Map.of());
        assertThatThrownBy(() -> incidents.createIfAbsent(
                TriageCommand.create("archived-service-fingerprint", payload, now)))
                .isInstanceOf(UnknownFleetServiceException.class);
    }

    @Test
    void fixedScenarioTemplatesUseGeneratedIdsAndCannotEncodeArbitraryActions() throws Exception {
        String created = mockMvc.perform(post("/api/v1/catalog/scenarios")
                        .with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "scenarioKey":"checkout-capacity",
                                  "displayName":"Checkout capacity saturation",
                                  "description":"Traffic exceeds the bounded checkout replica capacity.",
                                  "scenarioType":"CAPACITY_SATURATION",
                                  "serviceId":"20000000-0000-0000-0000-000000000002",
                                  "severity":"SEV2",
                                  "enabled":true
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.version").value(0))
                .andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(com.jayway.jsonpath.JsonPath.read(created, "$.id"));

        mockMvc.perform(put("/api/v1/catalog/scenarios/{id}", id)
                        .with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "scenarioKey":"checkout-capacity",
                                  "displayName":"Checkout capacity saturation",
                                  "description":"Updated bounded description.",
                                  "scenarioType":"SHELL_COMMAND",
                                  "serviceId":"20000000-0000-0000-0000-000000000002",
                                  "severity":"SEV2",
                                  "enabled":true,
                                  "version":0
                                }
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(delete("/api/v1/catalog/scenarios/{id}", id)
                        .queryParam("version", "0")
                        .with(admin()))
                .andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject(
                "select archived_at is not null and not enabled from demo_scenario_template where id = ?",
                Boolean.class, id)).isTrue();
    }

    private static JwtRequestPostProcessor viewer() {
        return jwt().authorities(() -> "ROLE_VIEWER");
    }

    private static JwtRequestPostProcessor admin() {
        return jwt().authorities(() -> "ROLE_ADMIN");
    }
}
