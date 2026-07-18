package io.mofazzal.sentinel.agent.retrieval;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "spring.ai.model.embedding", havingValue = "ollama")
public class SpringAiTextEmbeddingGateway implements TextEmbeddingGateway {

    private final EmbeddingModel model;

    public SpringAiTextEmbeddingGateway(EmbeddingModel model) {
        this.model = model;
    }

    @Override
    public float[] embed(String text) {
        return model.embed(text);
    }

    @Override
    public int dimensions() {
        return model.dimensions();
    }
}
