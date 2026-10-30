package com.hankang.aidocumentassistant;

import java.util.List;

public interface AnswerGenerator {
    boolean enabled();
    Generated generate(String question, List<DocumentSearch.Match> sources);
    record Generated(String answer, List<Integer> citations) {}
}
