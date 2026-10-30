package com.hankang.aidocumentassistant;

import java.util.List;

public interface EmbeddingClient {
    String model();
    List<float[]> embed(List<String> texts);
}
