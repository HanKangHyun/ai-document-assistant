package com.hankang.aidocumentassistant;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.util.List;

@Repository
public class DocumentStore {
    private final JdbcTemplate jdbc;
    public DocumentStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional
    public void save(DocumentInfo doc, List<TextChunker.Chunk> chunks) {
        jdbc.update("INSERT INTO documents(id, original_filename, stored_filename, file_size, page_count) VALUES (?,?,?,?,?)",
                doc.id(), doc.originalFilename(), doc.storedFilename(), doc.size(), doc.pageCount());
        jdbc.batchUpdate("INSERT INTO document_chunks(document_id,page_number,chunk_index,content) VALUES (?,?,?,?)",
                chunks, 100, (ps, c) -> {
                    ps.setString(1, doc.id()); ps.setInt(2, c.pageNumber());
                    ps.setInt(3, c.chunkIndex()); ps.setString(4, c.text());
                });
    }
    private static final String SELECT = "SELECT d.*, (SELECT COUNT(*) FROM document_chunks c WHERE c.document_id=d.id) AS chunk_count FROM documents d";
    private static final org.springframework.jdbc.core.RowMapper<DocumentInfo> MAPPER = (rs, row) ->
            new DocumentInfo(rs.getString("id"), rs.getString("original_filename"), rs.getString("stored_filename"),
                    rs.getLong("file_size"), rs.getInt("page_count"), rs.getInt("chunk_count"));

    public List<DocumentInfo> list() { return jdbc.query(SELECT + " ORDER BY created_at DESC", MAPPER); }
    public DocumentInfo get(String id) {
        return jdbc.query(SELECT + " WHERE d.id=?", MAPPER, id).stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "문서를 찾을 수 없습니다."));
    }
    public List<TextChunker.Chunk> chunks(String id) {
        get(id);
        return jdbc.query("SELECT page_number,chunk_index,content FROM document_chunks WHERE document_id=? ORDER BY page_number,chunk_index",
                (rs, row) -> new TextChunker.Chunk(rs.getInt(1), rs.getInt(2), rs.getString(3)), id);
    }
    public record DocumentInfo(String id, String originalFilename, String storedFilename,
                               long size, int pageCount, int chunkCount) {}
}
