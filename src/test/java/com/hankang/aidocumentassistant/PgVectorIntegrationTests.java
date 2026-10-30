package com.hankang.aidocumentassistant;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="PGVECTOR_TEST_URL", matches=".+")
class PgVectorIntegrationTests {
    static JdbcTemplate admin, jdbc;
    static DriverManagerDataSource dataSource;
    static String schema;
    static DocumentStore documents;
    static PgVectorStore vectors;
    @BeforeAll static void start() {
        String url = System.getenv("PGVECTOR_TEST_URL");
        String user = System.getenv().getOrDefault("POSTGRES_USER", "aiuser");
        String password = System.getenv("POSTGRES_PASSWORD");
        admin = new JdbcTemplate(new DriverManagerDataSource(url, user, password));
        admin.execute("CREATE EXTENSION IF NOT EXISTS vector WITH SCHEMA public");
        schema = "vector_test_" + UUID.randomUUID().toString().replace("-", "");
        admin.execute("CREATE SCHEMA " + schema);
        dataSource = new DriverManagerDataSource(url + (url.contains("?") ? "&" : "?") + "currentSchema=" + schema + ",public", user, password);
        new ResourceDatabasePopulator(new ClassPathResource("schema.sql"), new ClassPathResource("schema-postgres.sql")).execute(dataSource);
        jdbc = new JdbcTemplate(dataSource);
        documents = new DocumentStore(jdbc); vectors = new PgVectorStore(jdbc);
    }
    @AfterAll static void stop() {
        if (admin != null && schema != null) admin.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
    }
    String save(float[] vector) {
        String id = UUID.randomUUID().toString();
        var chunks = List.of(new TextChunker.Chunk(2, 0, "Jenkins 자동 배포"));
        documents.save(new DocumentStore.DocumentInfo(id, "test.pdf", id + ".pdf", 1, 2, 1), chunks);
        new TransactionTemplate(new JdbcTransactionManager(dataSource)).executeWithoutResult(s -> vectors.replace(id, "bge-m3:567m", chunks, List.of(vector)));
        return id;
    }
    @Test void cosineSearchIsScopedToDocumentAndModel() {
        var v = SemanticSearchTests.vector();
        String id = save(v); save(v);
        var hits = vectors.nearest(id, "bge-m3:567m", v, 0.35);
        assertEquals(1, hits.size()); assertEquals(2, hits.getFirst().pageNumber());
        assertEquals(1, hits.getFirst().similarity(), 0.0001);
        assertTrue(vectors.nearest(id, "other-model", v, 0.35).isEmpty());
        var orthogonal = new float[1024]; orthogonal[1] = 1;
        assertTrue(vectors.nearest(id, "bge-m3:567m", orthogonal, 0.35).isEmpty());
    }
    @Test void failedReplacementRollsBackAndReindexDoesNotDuplicate() {
        String id = save(SemanticSearchTests.vector());
        var tx = new TransactionTemplate(new JdbcTransactionManager(dataSource));
        var chunks = documents.chunks(id);
        assertThrows(IllegalArgumentException.class, () -> tx.executeWithoutResult(s -> vectors.replace(id, "bge-m3:567m", chunks, List.of(new float[2]))));
        assertEquals(1, vectors.count(id, "bge-m3:567m"));
        tx.executeWithoutResult(s -> vectors.replace(id, "bge-m3:567m", chunks, List.of(SemanticSearchTests.vector())));
        assertEquals(1, vectors.count(id, "bge-m3:567m"));
    }
}
