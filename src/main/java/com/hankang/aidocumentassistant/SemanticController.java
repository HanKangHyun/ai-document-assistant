package com.hankang.aidocumentassistant;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.util.List;

@RestController
@RequestMapping("/api/documents/{id}")
public class SemanticController {
    private final ObjectProvider<SemanticSearch> semantic;
    private final DocumentStore documents;
    public SemanticController(ObjectProvider<SemanticSearch> semantic, DocumentStore documents) {
        this.semantic = semantic; this.documents = documents;
    }
    @GetMapping("/index")
    public SemanticSearch.IndexStatus status(@PathVariable String id) {
        var service = semantic.getIfAvailable();
        if (service != null) return service.status(id);
        return new SemanticSearch.IndexStatus(false, false, 0, documents.get(id).chunkCount(), "");
    }
    @PostMapping("/index")
    public SemanticSearch.IndexStatus index(@PathVariable String id) { return required().reindex(id); }
    @GetMapping("/semantic-search")
    public List<PgVectorStore.Hit> search(@PathVariable String id, @RequestParam String q) { return required().search(id, q); }
    private SemanticSearch required() {
        var service = semantic.getIfAvailable();
        if (service == null) throw new ResponseStatusException(HttpStatus.CONFLICT, "의미 검색에는 PostgreSQL 실행 설정이 필요합니다.");
        return service;
    }
}
