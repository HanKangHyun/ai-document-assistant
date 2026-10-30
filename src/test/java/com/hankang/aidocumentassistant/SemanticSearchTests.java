package com.hankang.aidocumentassistant;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SemanticSearchTests {
    static class Documents extends DocumentStore {
        List<TextChunker.Chunk> chunks = List.of(new TextChunker.Chunk(2, 0, "Jenkins 자동 배포"));
        int reads;
        Documents() { super(null); }
        public DocumentInfo get(String id) { return new DocumentInfo(id, "test.pdf", "test.pdf", 1, 2, chunks.size()); }
        public List<TextChunker.Chunk> chunks(String id) { reads++; return chunks; }
    }
    static class Vectors extends PgVectorStore {
        int count, writes;
        String searchedId, searchedModel;
        List<Hit> hits = List.of();
        Vectors() { super(null); }
        public int count(String id, String model) { return count; }
        public void replace(String id, String model, List<TextChunker.Chunk> chunks, List<float[]> values) {
            assertEquals(chunks.size(), values.size()); count = values.size(); writes++;
        }
        public List<Hit> nearest(String id, String model, float[] query, double minScore) {
            searchedId=id; searchedModel=model; assertEquals(0.35,minScore); return hits;
        }
    }
    static class Embeddings implements EmbeddingClient {
        boolean fail;
        int calls;
        public String model() { return "bge-m3:567m"; }
        public List<float[]> embed(List<String> texts) {
            calls++; if (fail) throw new IllegalStateException("offline");
            assertTrue(texts.size() <= 8);
            return texts.stream().map(t -> vector()).toList();
        }
    }
    final Documents documents = new Documents();
    final Vectors vectors = new Vectors();
    final Embeddings embeddings = new Embeddings();
    final SemanticSearch search = new SemanticSearch(documents, vectors, embeddings, true, 0.35);
    static float[] vector() { float[] v = new float[1024]; v[0] = 1; return v; }
    @Test void missingIndexDoesNotCallEmbeddingModel() {
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> search.search("doc", "반복 업무")).getStatusCode().value());
        assertEquals(0, embeddings.calls);
    }
    @Test void indexingFailurePreservesExistingIndexAndReleasesLock() {
        vectors.count=1; embeddings.fail=true;
        assertThrows(IllegalStateException.class, () -> search.reindex("doc"));
        assertEquals(0, vectors.writes); assertEquals(1, vectors.count);
        embeddings.fail=false;
        assertTrue(search.reindex("doc").ready()); assertEquals(1, vectors.writes);
    }
    @Test void indexingBatchesAndStoresAllVectorsOnce() {
        var chunks = new ArrayList<TextChunker.Chunk>();
        for (int i = 0; i < 17; i++) chunks.add(new TextChunker.Chunk(1, i, "구간 " + i));
        documents.chunks=chunks;
        assertTrue(search.reindex("doc").ready());
        assertEquals(3, embeddings.calls); assertEquals(1, vectors.writes); assertEquals(17, vectors.count);
    }
    @Test void semanticQuestionBypassesLexicalFilterAndRetainsPage() {
        vectors.count=1; vectors.hits=List.of(new PgVectorStore.Hit(2,0,"Jenkins 자동 배포",0.7));
        var beans=new StaticListableBeanFactory(); beans.addBean("semantic",search);
        var retrieval=new DocumentSearch(documents,beans.getBeanProvider(SemanticSearch.class));
        assertEquals(2,retrieval.retrieve("doc","사람이 반복하던 작업을 줄인 사례").getFirst().pageNumber());
        assertEquals("doc",vectors.searchedId); assertEquals("bge-m3:567m",vectors.searchedModel);
        assertEquals(1,documents.reads);
    }
    @Test void noSimilarEvidenceReturnsEmptyAndInvalidQuestionIsRejected() {
        vectors.count=1;
        assertTrue(search.search("doc","희망 연봉").isEmpty());
        assertEquals(400,assertThrows(ResponseStatusException.class,()->search.search("doc","!!!")).getStatusCode().value());
    }
    @Test void disabledSearchRejectsIndexing() {
        var disabled=new SemanticSearch(documents,vectors,embeddings,false,0.35);
        assertFalse(disabled.status("doc").enabled());
        assertThrows(ResponseStatusException.class,()->disabled.reindex("doc"));
        assertEquals(0,embeddings.calls);
    }
}
