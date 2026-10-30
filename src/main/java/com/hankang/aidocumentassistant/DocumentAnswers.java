package com.hankang.aidocumentassistant;

import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@Service
public class DocumentAnswers {
    private final DocumentSearch search;
    private final AnswerGenerator generator;
    public DocumentAnswers(DocumentSearch search, AnswerGenerator generator) {
        this.search = search; this.generator = generator;
    }
    public Answer ask(String documentId, String question) {
        var matches = search.retrieve(documentId, question);
        if (matches.isEmpty()) return new Answer("NO_EVIDENCE", "문서에서 관련 근거를 찾지 못했습니다. 질문을 구체적으로 바꿔 보거나 키워드 검색으로 원문을 확인해 주세요.", List.of());
        if (!generator.enabled()) return new Answer("EXTRACTIVE", "AI 생성이 꺼져 있습니다. 질문과 관련된 원문 후보를 확인해 주세요.", sources(documentId, matches));
        var generated = generator.generate(question, matches);
        if (generated == null || generated.answer() == null || generated.citations() == null)
            throw invalid();
        if (generated.answer().isBlank() && generated.citations().isEmpty())
            return new Answer("NO_EVIDENCE", "검색된 내용만으로는 답변할 수 없습니다.", List.of());
        if (generated.answer().isBlank() || generated.answer().length() > 10000 || generated.citations().isEmpty()
                || generated.citations().stream().anyMatch(i -> i == null || i < 1 || i > matches.size()))
            throw invalid();
        var all = sources(documentId, matches);
        return new Answer("GENERATED", generated.answer(), generated.citations().stream().distinct().map(i -> all.get(i - 1)).toList());
    }
    private List<Source> sources(String id, List<DocumentSearch.Match> matches) {
        var result = new ArrayList<Source>();
        for (int i = 0; i < matches.size(); i++) {
            var match = matches.get(i);
            result.add(new Source(i + 1, match.pageNumber(), match.chunkIndex(), match.text(),
                    "/api/documents/" + id + "/pdf#page=" + match.pageNumber()));
        }
        return List.copyOf(result);
    }
    private ResponseStatusException invalid() {
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI 답변의 출처를 검증하지 못했습니다. 원문 검색으로 확인해 주세요.");
    }
    public record Source(int sourceId, int pageNumber, int chunkIndex, String text, String url) {}
    public record Answer(String mode, String answer, List<Source> sources) {}
}
