package io.mofazzal.sentinel.agent.retrieval;

import io.mofazzal.sentinel.tools.RunbookRetrieveTool;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@Import(RunbookSemanticRetrievalIntegrationTest.EmbeddingTestConfiguration.class)
@SpringBootTest(properties = {
        "spring.rabbitmq.listener.simple.auto-startup=false",
        "sentinel.agent.retrieval-mode=semantic",
        "sentinel.security.jwt-secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "sentinel.security.webhook-secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
})
class RunbookSemanticRetrievalIntegrationTest {

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
    private RunbookEmbeddingIndexer indexer;

    @Autowired
    private RunbookRetrieveTool runbookTool;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @WithMockUser(roles = "AGENT")
    void indexesIdempotentlyAndAppliesSimilarityThreshold() {
        assertThat(indexer.indexAll()).isEqualTo(3);
        assertThat(indexer.indexAll()).isEqualTo(3);
        assertThat(jdbc.queryForObject("select count(*) from runbook_embedding", Integer.class))
                .isEqualTo(3);

        assertThat(runbookTool.search("error rate spiked immediately after a bad release"))
                .singleElement()
                .satisfies(hit -> {
                    assertThat(hit.title()).contains("Rollback");
                    assertThat(hit.similarity()).isGreaterThanOrEqualTo(0.60);
                });

        assertThat(runbookTool.search("certificate authority mismatch in an unknown dependency"))
                .isEmpty();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class EmbeddingTestConfiguration {

        @Bean
        TextEmbeddingGateway deterministicEmbeddingGateway() {
            return new TextEmbeddingGateway() {
                @Override
                public float[] embed(String text) {
                    assertThat(org.springframework.transaction.support.TransactionSynchronizationManager
                            .isActualTransactionActive()).isFalse();
                    String normalized = text.toLowerCase(Locale.ROOT);
                    int axis;
                    if (normalized.contains("saturat") || normalized.contains("scale")
                            || normalized.contains("cpu")) {
                        axis = 2;
                    } else if (normalized.contains("restart") || normalized.contains("unhealthy")) {
                        axis = 1;
                    } else if (normalized.contains("rollback") || normalized.contains("bad release")
                            || normalized.contains("error rate") || normalized.contains("deployment")) {
                        axis = 0;
                    } else {
                        axis = 7;
                    }
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
