package com.hankang.aidocumentassistant;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
class TextChunkerTests {
    @Test void longPagesRetainOverlapAndPageNumbers() {
        String text="0123456789".repeat(175);
        var chunks=new TextChunker().chunk(List.of(new DocumentTextExtractor.PageText(3,text)));
        assertEquals(3,chunks.size());
        assertTrue(chunks.stream().allMatch(c->c.pageNumber()==3 && c.text().length()<=800));
        assertEquals(chunks.get(0).text().substring(650),chunks.get(1).text().substring(0,150));
        assertEquals(text.substring(1300),chunks.get(2).text());
    }
}
