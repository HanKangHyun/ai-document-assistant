package com.hankang.aidocumentassistant;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:context;MODE=PostgreSQL", "spring.datasource.username=sa", "spring.datasource.password="})
class AiDocumentAssistantApplicationTests {

    @Test
    void contextLoads() {
    }

}
