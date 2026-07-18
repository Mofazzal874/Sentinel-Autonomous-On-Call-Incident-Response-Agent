package io.mofazzal.sentinel.agent.retrieval;

public interface TextEmbeddingGateway {
    float[] embed(String text);

    int dimensions();
}
