package io.mofazzal.sentinel.agent.retrieval;

import io.mofazzal.sentinel.fleet.domain.RemediationActionType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class RunbookVectorQuery {

    private final JdbcTemplate jdbc;

    public RunbookVectorQuery(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<RunbookSearchEngine.RunbookMatch> search(
            String queryVector, double similarityThreshold, int limit) {
        return jdbc.query("""
                SELECT r.id, r.title, r.symptom_description, r.steps, r.action_type,
                       1 - (e.embedding <=> ?::vector) AS similarity
                FROM runbook_embedding e
                JOIN runbook r ON r.id = e.runbook_id
                WHERE 1 - (e.embedding <=> ?::vector) >= ?
                ORDER BY e.embedding <=> ?::vector
                LIMIT ?
                """, (result, row) -> new RunbookSearchEngine.RunbookMatch(
                        result.getObject("id", java.util.UUID.class),
                        result.getString("title"),
                        result.getString("symptom_description"),
                        result.getString("steps"),
                        RemediationActionType.valueOf(result.getString("action_type")),
                        result.getDouble("similarity")),
                queryVector, queryVector, similarityThreshold, queryVector, limit);
    }
}
