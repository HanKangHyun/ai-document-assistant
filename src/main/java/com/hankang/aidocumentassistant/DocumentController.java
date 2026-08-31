package com.hankang.aidocumentassistant;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final Path uploadDir = Path.of("uploads");
    private final DocumentTextExtractor textExtractor;
    private final TextChunker textChunker;

    public DocumentController(
            DocumentTextExtractor textExtractor,
            TextChunker textChunker
    ) {
        this.textExtractor = textExtractor;
        this.textChunker = textChunker;
    }

    @PostMapping(
            value = "/upload",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public UploadResponse upload(
            @RequestParam("file") MultipartFile file
    ) throws IOException {

        if (file.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "파일이 비어 있습니다."
            );
        }

        String originalFilename = file.getOriginalFilename();

        if (originalFilename == null ||
                !originalFilename.toLowerCase().endsWith(".pdf")) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "PDF 파일만 업로드할 수 있습니다."
            );
        }

        Files.createDirectories(uploadDir);

        String storedFilename = UUID.randomUUID() + ".pdf";
        Path target = uploadDir.resolve(storedFilename);

        Files.copy(
                file.getInputStream(),
                target,
                StandardCopyOption.REPLACE_EXISTING
        );

        return new UploadResponse(
                originalFilename,
                storedFilename,
                file.getSize()
        );
    }

    @GetMapping("/{filename}/text")
    public List<DocumentTextExtractor.PageText> extractText(
            @PathVariable String filename
    ) throws IOException {

        Path filePath = uploadDir.resolve(filename);

        if (!Files.exists(filePath)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "파일을 찾을 수 없습니다."
            );
        }

        return textExtractor.extractPages(filePath.toFile());
    }

    @GetMapping("/{filename}/chunks")
    public List<TextChunker.Chunk> getChunks(
            @PathVariable String filename
    ) throws IOException {

        Path filePath = uploadDir.resolve(filename);

        if (!Files.exists(filePath)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "파일을 찾을 수 없습니다."
            );
        }

        var pages = textExtractor.extractPages(filePath.toFile());

        return textChunker.chunk(pages);
    }

    public record UploadResponse(
            String originalFilename,
            String storedFilename,
            long size
    ) {
    }
}