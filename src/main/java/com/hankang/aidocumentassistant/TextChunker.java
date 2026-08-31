package com.hankang.aidocumentassistant;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class TextChunker {

    private static final int CHUNK_SIZE = 800;
    private static final int OVERLAP = 150;

    public List<Chunk> chunk(
            List<DocumentTextExtractor.PageText> pages
    ) {

        List<Chunk> chunks = new ArrayList<>();

        for (DocumentTextExtractor.PageText page : pages) {

            String text = page.text();
            int start = 0;
            int chunkIndex = 0;

            while (start < text.length()) {

                int end = Math.min(
                        start + CHUNK_SIZE,
                        text.length()
                );

                String chunkText =
                        text.substring(start, end).trim();

                chunks.add(
                        new Chunk(
                                page.pageNumber(),
                                chunkIndex++,
                                chunkText
                        )
                );

                if (end == text.length()) {
                    break;
                }

                start = end - OVERLAP;
            }
        }

        return chunks;
    }

    public record Chunk(
            int pageNumber,
            int chunkIndex,
            String text
    ) {}
}