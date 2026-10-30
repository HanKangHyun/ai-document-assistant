package com.hankang.aidocumentassistant;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class OllamaAnswerGeneratorTests {
    HttpServer server;
    String base;
    final JsonMapper json = JsonMapper.builder().build();
    final AtomicReference<String> received = new AtomicReference<>();
    @BeforeEach void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        base = "http://127.0.0.1:" + server.getAddress().getPort();
    }
    @AfterEach void stop() { server.stop(0); }
    void respond(int status, String body) {
        server.createContext("/api/chat", exchange -> {
            received.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            try (var out = exchange.getResponseBody()) { out.write(bytes); }
        });
        server.start();
    }
    AnswerGenerator.Generated generate() {
        return new OllamaAnswerGenerator(true, base, "test-model", 1)
                .generate("매출?", List.of(new DocumentSearch.Match(2, 0, "매출 20% 증가", 1)));
    }
    @Test void sendsBoundedSourcesAndParsesStructuredAnswer() {
        respond(200, json.writeValueAsString(Map.of("done", true, "message", Map.of("content", "{\"answer\":\"20% 증가\",\"citations\":[1]}"))));
        assertEquals(List.of(1), generate().citations());
        var sent = json.readTree(received.get());
        assertFalse(sent.path("stream").asBoolean());
        assertEquals("object", sent.path("format").path("type").asText());
        assertEquals(2, sent.path("format").path("required").size());
        assertEquals(1, sent.path("format").path("properties").path("citations").path("items").path("maximum").asInt());
        assertTrue(sent.path("messages").get(1).path("content").asText().contains("매출 20% 증가"));
    }
    @Test void providerErrorsAreSanitized() {
        respond(500, "secret server detail");
        var error = assertThrows(ResponseStatusException.class, this::generate);
        assertEquals(503, error.getStatusCode().value());
        assertFalse(error.getReason().contains("secret"));
    }
    @Test void malformedModelJsonFailsClosed() {
        respond(200, "{\"done\":true,\"message\":{\"content\":\"not json\"}}");
        assertEquals(502, assertThrows(ResponseStatusException.class, this::generate).getStatusCode().value());
    }
    @Test void rejectsRemoteEndpointsAndMissingModel() {
        assertThrows(IllegalArgumentException.class, () -> new OllamaAnswerGenerator(true, "https://example.com", "model", 1));
        assertThrows(IllegalArgumentException.class, () -> new OllamaAnswerGenerator(true, base, "", 1));
    }
    @Test void timeoutReturnsServiceUnavailable() {
        server.createContext("/api/chat", exchange -> {
            try { Thread.sleep(1500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            exchange.close();
        });
        server.start();
        assertEquals(504, assertThrows(ResponseStatusException.class, this::generate).getStatusCode().value());
    }
    @Test void truncatedGenerationIsRejected() {
        respond(200, "{\"done\":true,\"done_reason\":\"length\",\"message\":{\"content\":\"{}\"}}");
        var error = assertThrows(ResponseStatusException.class, this::generate);
        assertEquals(502, error.getStatusCode().value());
        assertTrue(error.getReason().contains("완료되지"));
    }
    @Test void validAbstentionRemainsEmptyInsteadOfAnError() {
        respond(200, json.writeValueAsString(Map.of("done", true, "message", Map.of("content", "{\"answer\":\"\",\"citations\":[]}"))));
        var result = generate();
        assertEquals("", result.answer());
        assertTrue(result.citations().isEmpty());
    }
    @Test void invalidFieldTypesAreFormatErrorsNotAbstention() {
        respond(200, json.writeValueAsString(Map.of("done", true, "message", Map.of("content", "{\"answer\":null,\"citations\":[]}"))));
        assertEquals(502, assertThrows(ResponseStatusException.class, this::generate).getStatusCode().value());
    }
}
