package com.hankang.aidocumentassistant;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DocumentSearchTests {
    @Test void koreanQuestionFindsCoreKeywordAndCorrectPage() {
        var store = mock(DocumentStore.class);
        when(store.chunks("doc")).thenReturn(List.of(
                new TextChunker.Chunk(1, 0, "배포 시간은 5분입니다."),
                new TextChunker.Chunk(2, 0, "매출 20% 증가를 기록했습니다.")));
        var results = new DocumentSearch(store).search("doc", "매출은 얼마나 증가했나요?");
        assertEquals(1, results.size());
        assertEquals(2, results.getFirst().pageNumber());
    }
}
