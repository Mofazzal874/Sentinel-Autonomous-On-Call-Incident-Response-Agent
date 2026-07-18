package io.mofazzal.sentinel.agent.retrieval;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnBean(EmbeddingModel.class)
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
