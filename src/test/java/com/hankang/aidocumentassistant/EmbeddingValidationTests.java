package com.hankang.aidocumentassistant;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class EmbeddingValidationTests {
    final OllamaEmbeddings embeddings = new OllamaEmbeddings("http://127.0.0.1:11434");
    final JsonMapper json = JsonMapper.builder().build();
    String body(float[] vector) { return json.writeValueAsString(Map.of("embeddings", List.of(vector))); }
    @Test void parses1024DimensionalVector() {
        var vector = SemanticSearchTests.vector();
        assertArrayEquals(vector, embeddings.parse(body(vector), 1).getFirst());
    }
    @Test void rejectsWrongCountDimensionAndZeroVector() {
        assertThrows(ResponseStatusException.class, () -> embeddings.parse(body(new float[3]), 1));
        assertThrows(ResponseStatusException.class, () -> embeddings.parse(body(new float[1024]), 1));
        assertThrows(ResponseStatusException.class, () -> embeddings.parse(body(SemanticSearchTests.vector()), 2));
    }
    @Test void rejectsNonNumericValues() {
        var values = new ArrayList<Object>(Collections.nCopies(1024, 0)); values.set(0, "1");
        assertThrows(ResponseStatusException.class, () -> embeddings.parse(json.writeValueAsString(Map.of("embeddings", List.of(values))), 1));
    }
    @Test void vectorSqlSerializationRejectsInvalidNumbers() {
        var v = SemanticSearchTests.vector(); v[1] = Float.NaN;
        assertThrows(IllegalArgumentException.class, () -> PgVectorStore.literal(v));
        assertThrows(IllegalArgumentException.class, () -> PgVectorStore.literal(new float[1024]));
        assertThrows(IllegalArgumentException.class, () -> PgVectorStore.literal(new float[1]));
    }
    @Test void rejectsRemoteOllamaUrl() {
        assertThrows(IllegalArgumentException.class, () -> new OllamaEmbeddings("https://example.com"));
    }
}
