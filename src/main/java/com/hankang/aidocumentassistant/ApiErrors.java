package com.hankang.aidocumentassistant;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;

@RestControllerAdvice
public class ApiErrors {
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String,String>> request(ResponseStatusException e) {
        return ResponseEntity.status(e.getStatusCode()).body(Map.of("message",
                e.getReason() == null ? "요청을 처리할 수 없습니다." : e.getReason()));
    }
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String,String>> size() {
        return ResponseEntity.status(413).body(Map.of("message", "PDF는 20MB 이하로 업로드해 주세요."));
    }
}
