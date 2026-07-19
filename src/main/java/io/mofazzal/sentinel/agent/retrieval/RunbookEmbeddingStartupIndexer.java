package io.mofazzal.sentinel.agent.retrieval;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "sentinel.agent.index-runbooks-on-startup", havingValue = "true")
public class RunbookEmbeddingStartupIndexer implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(RunbookEmbeddingStartupIndexer.class);

    private final RunbookEmbeddingIndexer indexer;

    public RunbookEmbeddingStartupIndexer(RunbookEmbeddingIndexer indexer) {
        this.indexer = indexer;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        int indexedDocuments = indexer.indexAll();
        LOGGER.info("Indexed {} runbook documents for semantic retrieval", indexedDocuments);
    }
}
