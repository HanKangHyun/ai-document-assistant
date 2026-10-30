package com.hankang.aidocumentassistant;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Repository
@Profile("postgres")
public class PgVectorStore {
    private final JdbcTemplate jdbc;
    public PgVectorStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public int count(String id, String model) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM chunk_embeddings WHERE document_id=? AND model=?", Integer.class, id, model);
    }
    @Transactional
    public void replace(String id, String model, List<TextChunker.Chunk> chunks, List<float[]> vectors) {
        if (chunks.size() != vectors.size()) throw new IllegalArgumentException("Vector count mismatch");
        // Generation happens before this transaction. Serialize replacements for this document.
        jdbc.queryForObject("SELECT id FROM documents WHERE id=? FOR UPDATE", String.class, id);
        jdbc.update("DELETE FROM chunk_embeddings WHERE document_id=? AND model=?", id, model);
        for (int i = 0; i < chunks.size(); i++) {
            var chunk = chunks.get(i);
            jdbc.update("INSERT INTO chunk_embeddings(document_id,page_number,chunk_index,model,embedding) VALUES (?,?,?,?,CAST(? AS vector))",
                    id, chunk.pageNumber(), chunk.chunkIndex(), model, literal(vectors.get(i)));
        }
    }
    public List<Hit> nearest(String id, String model, float[] query, double minScore) {
        return jdbc.query("""
                SELECT c.page_number,c.chunk_index,c.content,1-(e.embedding <=> CAST(? AS vector)) AS score
                FROM chunk_embeddings e JOIN document_chunks c
                  ON c.document_id=e.document_id AND c.page_number=e.page_number AND c.chunk_index=e.chunk_index
                WHERE e.document_id=? AND e.model=? AND 1-(e.embedding <=> CAST(? AS vector)) >= ?
                ORDER BY e.embedding <=> CAST(? AS vector),c.page_number,c.chunk_index LIMIT 5
                """, (rs, n) -> new Hit(rs.getInt(1), rs.getInt(2), rs.getString(3), rs.getDouble(4)),
                literal(query), id, model, literal(query), minScore, literal(query));
    }
    static String literal(float[] vector) {
        if (vector.length != OllamaEmbeddings.DIMENSIONS) throw new IllegalArgumentException("Expected 1024 dimensions");
        var result = new StringJoiner(",", "[", "]");
        double norm = 0;
        for (float value : vector) {
            if (!Float.isFinite(value)) throw new IllegalArgumentException("Non-finite vector");
            norm += (double) value * value;
            result.add(Float.toString(value));
        }
        if (norm == 0) throw new IllegalArgumentException("Zero vector");
        return result.toString();
    }
    public record Hit(int pageNumber, int chunkIndex, String text, double similarity) {}
}
