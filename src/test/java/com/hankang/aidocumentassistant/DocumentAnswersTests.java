package com.hankang.aidocumentassistant;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DocumentAnswersTests {
    final DocumentSearch search = mock(DocumentSearch.class);
    final AnswerGenerator generator = mock(AnswerGenerator.class);
    final DocumentAnswers answers = new DocumentAnswers(search, generator);
    final List<DocumentSearch.Match> matches = List.of(new DocumentSearch.Match(3, 0, "Revenue rose 20%.", 1));
    @Test void noEvidenceNeverCallsModel() {
        when(search.retrieve("doc", "unknown")).thenReturn(List.of());
        assertEquals("NO_EVIDENCE", answers.ask("doc", "unknown").mode());
        verifyNoInteractions(generator);
    }
    @Test void disabledModelReturnsHonestExtractiveResult() {
        when(search.retrieve("doc", "revenue")).thenReturn(matches);
        var result = answers.ask("doc", "revenue");
        assertEquals("EXTRACTIVE", result.mode());
        assertEquals("/api/documents/doc/pdf#page=3", result.sources().getFirst().url());
        verify(generator, never()).generate(anyString(), anyList());
    }
    @Test void generatedAnswerUsesServerOwnedSources() {
        when(search.retrieve("doc", "revenue")).thenReturn(matches);
        when(generator.enabled()).thenReturn(true);
        when(generator.generate("revenue", matches)).thenReturn(new AnswerGenerator.Generated("20% 증가", List.of(1, 1)));
        var result = answers.ask("doc", "revenue");
        assertEquals("GENERATED", result.mode());
        assertEquals(1, result.sources().size());
        assertEquals("Revenue rose 20%.", result.sources().getFirst().text());
    }
    @Test void fabricatedOrAbsentCitationsAreRejected() {
        when(search.retrieve("doc", "revenue")).thenReturn(matches);
        when(generator.enabled()).thenReturn(true);
        for (var citations : List.of(List.of(2), List.of(0), List.<Integer>of())) {
            when(generator.generate("revenue", matches)).thenReturn(new AnswerGenerator.Generated("unsupported", citations));
            assertEquals(502, assertThrows(ResponseStatusException.class, () -> answers.ask("doc", "revenue")).getStatusCode().value());
        }
    }
    @Test void modelCanAbstain() {
        when(search.retrieve("doc", "revenue")).thenReturn(matches);
        when(generator.enabled()).thenReturn(true);
        when(generator.generate("revenue", matches)).thenReturn(new AnswerGenerator.Generated("", List.of()));
        assertEquals("NO_EVIDENCE", answers.ask("doc", "revenue").mode());
    }
}

