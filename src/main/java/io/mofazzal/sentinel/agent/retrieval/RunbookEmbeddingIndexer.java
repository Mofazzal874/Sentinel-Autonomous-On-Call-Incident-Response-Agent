package io.mofazzal.sentinel.agent.retrieval;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "sentinel.agent.retrieval-mode", havingValue = "semantic")
public class RunbookEmbeddingIndexer {

    private final RunbookDocumentReader reader;
    private final RunbookVectorWriter writer;
    private final TextEmbeddingGateway embeddings;

    public RunbookEmbeddingIndexer(RunbookDocumentReader reader, RunbookVectorWriter writer,
                                    TextEmbeddingGateway embeddings) {
        this.reader = reader;
        this.writer = writer;
        this.embeddings = embeddings;
    }

    public int indexAll() {
        requireDimensions();
        var documents = reader.readAll();
        documents.forEach(document -> writer.upsert(document, embeddings.embed(document.embeddingContent())));
        return documents.size();
    }

    private void requireDimensions() {
        if (embeddings.dimensions() != RunbookVectorWriter.DIMENSIONS) {
            throw new IllegalStateException("Embedding model must produce 768 dimensions, but produced "
                    + embeddings.dimensions());
        }
    }
}
