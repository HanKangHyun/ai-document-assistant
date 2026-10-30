package com.hankang.aidocumentassistant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import java.util.concurrent.Semaphore;

@Service
@Profile("postgres")
public class SemanticSearch {
    private final DocumentStore documents;
    private final PgVectorStore vectors;
    private final EmbeddingClient embeddings;
    private final boolean enabled;
    private final double minScore;
    private final Semaphore indexing = new Semaphore(1);
    public SemanticSearch(DocumentStore documents, PgVectorStore vectors, EmbeddingClient embeddings,
            @Value("${app.search.semantic-enabled:false}") boolean enabled,
            @Value("${app.search.min-similarity:0.35}") double minScore) {
        this.documents = documents; this.vectors = vectors; this.embeddings = embeddings;
        this.enabled = enabled; this.minScore = minScore;
        if (!Double.isFinite(minScore) || minScore < 0 || minScore > 1) throw new IllegalArgumentException("Invalid similarity threshold");
    }
    public boolean enabled() { return enabled; }
    public IndexStatus status(String id) {
        var document = documents.get(id);
        int indexed = enabled ? vectors.count(id, embeddings.model()) : 0;
        return new IndexStatus(enabled, enabled && indexed == document.chunkCount() && indexed > 0,
                indexed, document.chunkCount(), embeddings.model());
    }
    public IndexStatus reindex(String id) {
        requireEnabled();
        if (!indexing.tryAcquire()) throw new ResponseStatusException(HttpStatus.CONFLICT, "다른 문서를 준비 중입니다. 잠시 후 다시 시도해 주세요.");
        try {
            var chunks = documents.chunks(id);
            if (chunks.size() > 2000) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "의미 검색 준비는 문서당 2,000개 구간까지 지원합니다.");
            var generated = new ArrayList<float[]>();
            for (int offset = 0; offset < chunks.size(); offset += 8) {
                var batch = chunks.subList(offset, Math.min(offset + 8, chunks.size()));
                var result = embeddings.embed(batch.stream().map(TextChunker.Chunk::text).toList());
                if (result.size() != batch.size()) throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "임베딩 응답 개수가 올바르지 않습니다.");
                result.forEach(PgVectorStore::literal);
                generated.addAll(result);
            }
            vectors.replace(id, embeddings.model(), chunks, generated);
            return status(id);
        } finally { indexing.release(); }
    }
    public List<PgVectorStore.Hit> search(String id, String query) {
        requireEnabled();
        DocumentSearch.validate(query);
        if (!status(id).ready()) throw new ResponseStatusException(HttpStatus.CONFLICT,
                "이 문서의 의미 검색 준비가 필요합니다. ‘의미 검색 준비’ 버튼을 눌러 주세요.");
        var queryVector = embeddings.embed(List.of(query)).getFirst();
        return vectors.nearest(id, embeddings.model(), queryVector, minScore);
    }
    private void requireEnabled() {
        if (!enabled) throw new ResponseStatusException(HttpStatus.CONFLICT, "의미 검색이 꺼져 있습니다. PostgreSQL과 Semantic 옵션으로 실행해 주세요.");
    }
    public record IndexStatus(boolean enabled, boolean ready, int indexedChunks, int totalChunks, String model) {}
}
