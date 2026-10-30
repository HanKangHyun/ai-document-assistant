package com.hankang.aidocumentassistant;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/documents")
public class AnswerController {
    private final DocumentAnswers answers;
    public AnswerController(DocumentAnswers answers) { this.answers = answers; }
    @PostMapping("/{id}/ask")
    public DocumentAnswers.Answer ask(@PathVariable String id, @RequestBody Question request) {
        return answers.ask(id, request.question());
    }
    public record Question(String question) {}
}
