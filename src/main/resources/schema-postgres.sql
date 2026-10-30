CREATE EXTENSION IF NOT EXISTS vector;
CREATE TABLE IF NOT EXISTS chunk_embeddings (
    document_id VARCHAR(36) NOT NULL,
    page_number INTEGER NOT NULL,
    chunk_index INTEGER NOT NULL,
    model VARCHAR(100) NOT NULL,
    embedding vector(1024) NOT NULL,
    PRIMARY KEY (document_id, page_number, chunk_index, model),
    FOREIGN KEY (document_id,page_number,chunk_index)
        REFERENCES document_chunks(document_id,page_number,chunk_index) ON DELETE CASCADE
);
