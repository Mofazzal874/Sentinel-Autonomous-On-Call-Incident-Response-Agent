package io.mofazzal.sentinel.agent.retrieval;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnBean(TextEmbeddingGateway.class)
@ConditionalOnProperty(name = "sentinel.agent.retrieval-mode", havingValue = "semantic")
public class SemanticRunbookSearchEngine implements RunbookSearchEngine {

    private static final double SIMILARITY_THRESHOLD = 0.60;
    private final TextEmbeddingGateway embeddings;
    private final RunbookVectorQuery vectors;

    public SemanticRunbookSearchEngine(TextEmbeddingGateway embeddings, RunbookVectorQuery vectors) {
        this.embeddings = embeddings;
        this.vectors = vectors;
    }

    @Override
    public List<RunbookMatch> search(String symptom, int limit) {
        if (embeddings.dimensions() != RunbookVectorWriter.DIMENSIONS) {
            throw new IllegalStateException("Embedding model dimensions do not match runbook vector schema");
        }
        String queryVector = VectorLiteral.from(embeddings.embed(symptom), RunbookVectorWriter.DIMENSIONS);
        return vectors.search(queryVector, SIMILARITY_THRESHOLD, limit);
    }
}
