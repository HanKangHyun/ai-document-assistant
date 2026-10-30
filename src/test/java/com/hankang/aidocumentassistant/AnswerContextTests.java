package com.hankang.aidocumentassistant;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AnswerContextTests {
    DocumentSearch.Match hit(int page, int index, String text) { return new DocumentSearch.Match(page,index,text,0); }
    @Test void restoresHeadingAndFollowingContextOnSamePage() {
        var chunks=List.of(new TextChunker.Chunk(2,0,"Jenkins 기반 자동 배포"),
                new TextChunker.Chunk(2,1,"수동 절차를 스크립트로 구현"),new TextChunker.Chunk(2,2,"운영 폴더 백업 후 재시작"),
                new TextChunker.Chunk(3,0,"다른 페이지의 연봉 정보"));
        var result=AnswerContext.expand(List.of(hit(2,1,chunks.get(1).text())),chunks);
        assertEquals(1,result.size()); assertEquals(2,result.getFirst().pageNumber());
        assertEquals("Jenkins 기반 자동 배포\n수동 절차를 스크립트로 구현\n운영 폴더 백업 후 재시작",result.getFirst().text());
        assertFalse(result.getFirst().text().contains("연봉"));
    }
    @Test void removesRealChunkOverlapAndDoesNotDuplicateCoveredHit() {
        var text=new StringBuilder(); for(int i=0;i<300;i++) text.append(String.format("word%04d ",i));
        var chunks=new TextChunker().chunk(List.of(new DocumentTextExtractor.PageText(2,text.toString())));
        var result=AnswerContext.expand(List.of(hit(2,1,chunks.get(1).text()),hit(2,0,chunks.getFirst().text())),chunks);
        assertEquals(1,result.size());
        assertEquals(text.substring(0,2100).trim(),result.getFirst().text());
    }
    @Test void preservesRankAndNeverJoinsPagesOrNonAdjacentChunks() {
        var chunks=List.of(new TextChunker.Chunk(4,0,"먼저 찾은 근거"),new TextChunker.Chunk(2,0,"관련 근거"),new TextChunker.Chunk(2,5,"멀리 떨어진 내용"));
        var result=AnswerContext.expand(List.of(hit(4,0,"먼저 찾은 근거"),hit(2,0,"관련 근거")),chunks);
        assertEquals(List.of(4,2),result.stream().map(DocumentSearch.Match::pageNumber).toList());
        assertEquals("관련 근거",result.get(1).text());
    }
    @Test void noEvidenceStaysEmpty() { assertTrue(AnswerContext.expand(List.of(),List.of(new TextChunker.Chunk(1,0,"text"))).isEmpty()); }
    @Test void repeatedShortWordsAreNotDeleted() {
        var result=AnswerContext.expand(List.of(hit(1,0,"Java")),List.of(new TextChunker.Chunk(1,0,"Java"),new TextChunker.Chunk(1,1,"Java 교육")));
        assertEquals("Java\nJava 교육",result.getFirst().text());
    }
}
