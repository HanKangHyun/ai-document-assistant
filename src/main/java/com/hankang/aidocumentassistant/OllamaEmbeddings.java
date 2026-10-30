package com.hankang.aidocumentassistant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.io.IOException;
import java.util.*;

@Service
@Profile("postgres")
public class OllamaEmbeddings implements EmbeddingClient {
    public static final int DIMENSIONS = 1024;
    private final URI endpoint;
    private static class ClientHolder {
        static final HttpClient CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }
    private final JsonMapper json = JsonMapper.builder().build();
    public OllamaEmbeddings(@Value("${app.ai.base-url:http://127.0.0.1:11434}") String baseUrl) {
        endpoint = URI.create(baseUrl + "/api/embed");
        if (!"http".equals(endpoint.getScheme()) || !Set.of("localhost", "127.0.0.1", "[::1]").contains(endpoint.getHost())
                || endpoint.getUserInfo() != null || endpoint.getQuery() != null || endpoint.getFragment() != null)
            throw new IllegalArgumentException("Embeddings require a local Ollama HTTP endpoint");
    }
    public String model() { return "bge-m3:567m"; }
    public List<float[]> embed(List<String> texts) {
        if (texts.isEmpty() || texts.size() > 8 || texts.stream().anyMatch(t -> t == null || t.isBlank() || t.length() > 4000))
            throw new IllegalArgumentException("Expected 1..8 bounded nonempty texts");
        var payload = Map.of("model", model(), "input", texts, "truncate", false, "keep_alive", "0s");
        try {
            var response = ClientHolder.CLIENT.send(HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(120))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(payload))).build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) throw unavailable();
            return parse(response.body(), texts.size());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); throw unavailable();
        } catch (IOException | RuntimeException e) { throw unavailable(); }
    }
    List<float[]> parse(String body, int count) {
        var rows = json.readTree(body).path("embeddings");
        if (!rows.isArray() || rows.size() != count) throw unavailable();
        var vectors = new ArrayList<float[]>();
        for (var row : rows) {
            if (!row.isArray() || row.size() != DIMENSIONS) throw unavailable();
            float[] vector = new float[DIMENSIONS];
            double norm = 0;
            for (int i = 0; i < DIMENSIONS; i++) {
                if (!row.get(i).isNumber()) throw unavailable();
                vector[i] = (float) row.get(i).asDouble();
                if (!Float.isFinite(vector[i])) throw unavailable();
                norm += (double) vector[i] * vector[i];
            }
            if (norm == 0) throw unavailable();
            vectors.add(vector);
        }
        return List.copyOf(vectors);
    }
    private ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "문서 의미 분석에 실패했습니다. Ollama 실행 및 bge-m3:567m 설치를 확인해 주세요. 기존 키워드 검색은 사용할 수 있습니다.");
    }
}
