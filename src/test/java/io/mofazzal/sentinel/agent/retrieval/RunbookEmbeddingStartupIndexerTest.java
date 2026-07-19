package io.mofazzal.sentinel.agent.retrieval;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RunbookEmbeddingStartupIndexerTest {

    @Test
    void indexesEveryRunbookDuringStartup() {
        RunbookEmbeddingIndexer indexer = mock(RunbookEmbeddingIndexer.class);
        when(indexer.indexAll()).thenReturn(3);

        new RunbookEmbeddingStartupIndexer(indexer).run(new DefaultApplicationArguments());

        verify(indexer).indexAll();
    }
}
