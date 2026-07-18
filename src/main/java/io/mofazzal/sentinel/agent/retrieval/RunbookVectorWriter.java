package io.mofazzal.sentinel.agent.retrieval;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Component
public class RunbookVectorWriter {

    static final int DIMENSIONS = 768;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public RunbookVectorWriter(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void upsert(RunbookDocument document, float[] embedding) {
        String vector = VectorLiteral.from(embedding, DIMENSIONS);
        jdbc.update("""
                INSERT INTO runbook_embedding (runbook_id, content, embedding, embedded_at)
                VALUES (?, ?, ?::vector, ?)
                ON CONFLICT (runbook_id) DO UPDATE SET
                    content = EXCLUDED.content,
                    embedding = EXCLUDED.embedding,
                    embedded_at = EXCLUDED.embedded_at
                """, document.id(), document.embeddingContent(), vector,
                java.sql.Timestamp.from(clock.instant()));
    }
}
