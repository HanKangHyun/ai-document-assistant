package com.hankang.aidocumentassistant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {
    private final Path uploadDir;
    private final DocumentTextExtractor extractor;
    private final TextChunker chunker;
    private final DocumentStore store;
    private final DocumentSearch search;
    public DocumentController(@Value("${app.upload-dir:uploads}") String directory,
                              DocumentTextExtractor extractor, TextChunker chunker,
                              DocumentStore store, DocumentSearch search) {
        this.uploadDir = Path.of(directory).toAbsolutePath().normalize();
        this.extractor = extractor; this.chunker = chunker; this.store = store; this.search = search;
    }
    @GetMapping
    public List<DocumentStore.DocumentInfo> list() { return store.list(); }
    @PostMapping(value="/upload", consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentStore.DocumentInfo upload(@RequestParam("file") MultipartFile file) throws IOException {
        String name = file.getOriginalFilename();
        if (file.isEmpty() || name == null || !name.toLowerCase(Locale.ROOT).endsWith(".pdf"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "내용이 있는 PDF 파일을 선택해 주세요.");
        name = name.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1);
        if (name.length() > 255) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "파일명이 너무 깁니다.");
        String id = UUID.randomUUID().toString();
        String filename = id + ".pdf";
        Files.createDirectories(uploadDir);
        Path target = uploadDir.resolve(filename);
        boolean saved = false;
        try {
            try (var input = file.getInputStream()) { Files.copy(input, target); }
            List<DocumentTextExtractor.PageText> pages;
            try { pages = extractor.extractPages(target.toFile()); }
            catch (IOException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "읽을 수 없는 PDF입니다. 손상되었거나 암호가 설정되었는지 확인해 주세요.");
            }
            var chunks = chunker.chunk(pages);
            if (chunks.isEmpty()) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "텍스트가 없는 PDF입니다. 스캔 문서는 문자 인식(OCR) 후 업로드해 주세요.");
            var doc = new DocumentStore.DocumentInfo(id, name, filename, file.getSize(), pages.size(), chunks.size());
            store.save(doc, chunks);
            saved = true;
            return doc;
        } finally { if (!saved) Files.deleteIfExists(target); }
    }
    @GetMapping("/{filename}/text")
    public List<DocumentTextExtractor.PageText> text(@PathVariable String filename) throws IOException {
        return extractor.extractPages(resolve(filename).toFile());
    }
    @GetMapping("/{filename}/chunks")
    public List<TextChunker.Chunk> chunks(@PathVariable String filename) throws IOException {
        return chunker.chunk(extractor.extractPages(resolve(filename).toFile()));
    }
    @GetMapping("/{id}/search")
    public List<DocumentSearch.Match> search(@PathVariable String id, @RequestParam String q) {
        return search.search(id, q);
    }
    @GetMapping(value="/{id}/pdf", produces=MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<Resource> pdf(@PathVariable String id) {
        var doc = store.get(id);
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"document.pdf\"")
                .header("X-Content-Type-Options", "nosniff")
                .body(new FileSystemResource(resolve(doc.storedFilename())));
    }
    private Path resolve(String filename) {
        if (!filename.matches("[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}\\.pdf"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "올바르지 않은 파일명입니다.");
        Path path = uploadDir.resolve(filename).normalize();
        if (!path.startsWith(uploadDir) || !Files.isRegularFile(path))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "파일을 찾을 수 없습니다.");
        return path;
    }
}
