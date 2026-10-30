package com.hankang.aidocumentassistant;

import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@Service
public class DocumentSearch {
    private final DocumentStore store;
    private final SemanticSearch semantic;
    public DocumentSearch(DocumentStore store) { this.store = store; this.semantic = null; }
    @org.springframework.beans.factory.annotation.Autowired
    public DocumentSearch(DocumentStore store, org.springframework.beans.factory.ObjectProvider<SemanticSearch> semantic) {
        this.store = store; this.semantic = semantic.getIfAvailable();
    }
    public List<Match> retrieve(String id, String query) {
        List<Match> hits = semantic != null && semantic.enabled()
                ? semantic.search(id, query).stream().map(h -> new Match(h.pageNumber(), h.chunkIndex(), h.text(), 0)).toList()
                : search(id, query);
        return hits.isEmpty() ? hits : AnswerContext.expand(hits, store.chunks(id));
    }
    static void validate(String query) {
        if (query == null || query.isBlank() || query.length() > 500)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "검색어는 1~500자로 입력해 주세요.");
        if (!query.matches("(?s).*[\\p{L}\\p{N}].*"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "문자나 숫자를 입력해 주세요.");
    }
    public List<Match> search(String id, String query) {
        validate(query);
        var terms = Arrays.stream(query.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+"))
                .filter(t -> !t.isBlank()).map(DocumentSearch::normalizeTerm)
                .filter(t -> !STOP_WORDS.contains(t)).distinct().toList();
        if (terms.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "문자나 숫자를 입력해 주세요.");
        return store.chunks(id).stream().map(c -> {
            String text = c.text().toLowerCase(Locale.ROOT);
            int hits = (int) terms.stream().filter(text::contains).count();
            return new Match(c.pageNumber(), c.chunkIndex(), c.text(), hits);
        }).filter(m -> m.matchedTerms() > 0)
                .sorted(Comparator.comparingInt(Match::matchedTerms).reversed()
                        .thenComparingInt(Match::pageNumber).thenComparingInt(Match::chunkIndex))
                .limit(5).toList();
    }
    public record Match(int pageNumber, int chunkIndex, String text, int matchedTerms) {}
    private static final Set<String> STOP_WORDS = Set.of("얼마나", "무엇", "어떻게", "알려줘", "알려주세요", "인가요", "있나요", "what", "is", "the", "how", "much", "does", "did", "a", "an");
    // Lightweight query normalization; semantic retrieval and morphological analysis are not implemented.
    static String normalizeTerm(String term) {
        if (term.matches("[가-힣]{3,}")) {
            for (String suffix : List.of("에서는", "으로", "에서", "은", "는", "이", "가", "을", "를", "의")) {
                if (term.endsWith(suffix) && term.length() - suffix.length() >= 2)
                    return term.substring(0, term.length() - suffix.length());
            }
        }
        return term;
    }
}
