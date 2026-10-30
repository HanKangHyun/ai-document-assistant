package com.hankang.aidocumentassistant;

import java.util.*;

/** Builds bounded, page-local evidence without changing stored chunks or embeddings. */
public final class AnswerContext {
    private AnswerContext() {}
    public static List<DocumentSearch.Match> expand(List<DocumentSearch.Match> hits, List<TextChunker.Chunk> chunks) {
        var result = new ArrayList<DocumentSearch.Match>();
        var used = new HashSet<Key>();
        int remaining = 12000;
        for (var hit : hits.stream().limit(5).toList()) {
            var anchor = new Key(hit.pageNumber(), hit.chunkIndex());
            if (used.contains(anchor)) continue;
            var window = chunks.stream().filter(c -> c.pageNumber() == hit.pageNumber()
                    && Math.abs((long) c.chunkIndex() - hit.chunkIndex()) <= 1
                    && !used.contains(new Key(c.pageNumber(), c.chunkIndex())))
                    .sorted(Comparator.comparingInt(TextChunker.Chunk::chunkIndex)).toList();
            if (window.stream().noneMatch(c -> c.chunkIndex() == hit.chunkIndex())) continue;
            var text = new StringBuilder();
            TextChunker.Chunk previous = null;
            for (var chunk : window) {
                if (previous == null) text.append(chunk.text());
                else {
                    int overlap = previous.chunkIndex() + 1 == chunk.chunkIndex()
                            ? overlap(previous.text(), chunk.text()) : 0;
                    if (overlap == 0) text.append('\n');
                    text.append(chunk.text().substring(overlap));
                }
                previous = chunk;
            }
            // Keep the complete anchor rather than silently truncating evidence.
            if (text.length() > 2402 || text.length() > remaining) {
                if (hit.text().length() > remaining) continue;
                result.add(hit); remaining -= hit.text().length(); used.add(anchor);
            } else {
                result.add(new DocumentSearch.Match(hit.pageNumber(), window.getFirst().chunkIndex(), text.toString(), hit.matchedTerms()));
                remaining -= text.length();
                window.forEach(c -> used.add(new Key(c.pageNumber(), c.chunkIndex())));
            }
        }
        return List.copyOf(result);
    }
    private static int overlap(String left, String right) {
        for (int n = Math.min(150, Math.min(left.length(), right.length())); n >= 20; n--)
            if (left.regionMatches(left.length() - n, right, 0, n)) return n;
        return 0;
    }
    private record Key(int page, int index) {}
}
