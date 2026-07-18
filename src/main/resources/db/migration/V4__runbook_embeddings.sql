CREATE TABLE runbook_embedding (
    runbook_id UUID PRIMARY KEY REFERENCES runbook(id),
    content TEXT NOT NULL,
    embedding VECTOR(768) NOT NULL,
    embedded_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_runbook_embedding_content_not_blank CHECK (btrim(content) <> '')
);

CREATE INDEX ix_runbook_embedding_hnsw
    ON runbook_embedding USING HNSW (embedding vector_cosine_ops);
