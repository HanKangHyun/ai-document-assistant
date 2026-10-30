package com.hankang.aidocumentassistant;

import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.font.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties={"spring.datasource.url=jdbc:h2:mem:flow;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "spring.datasource.username=sa", "spring.datasource.password="})
class DocumentFlowTests {
    static final Path uploads;
    static { try { uploads=Files.createTempDirectory("document-flow-"); } catch(IOException e) { throw new UncheckedIOException(e); } }
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) { r.add("app.upload-dir", uploads::toString); }
    @Value("${local.server.port}") int port;
    @Autowired DocumentStore store;
    final HttpClient http=HttpClient.newHttpClient();
    URI url(String path) { return URI.create("http://127.0.0.1:"+port+path); }
    HttpResponse<String> get(String path) throws Exception {
        return http.send(HttpRequest.newBuilder(url(path)).GET().build(),HttpResponse.BodyHandlers.ofString());
    }
    HttpResponse<String> upload(String name, byte[] pdf) throws Exception {
        String boundary="test-boundary";
        var bytes=new ByteArrayOutputStream();
        bytes.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\"file\"; filename=\""+name+"\"\r\nContent-Type: application/pdf\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        bytes.write(pdf); bytes.write(("\r\n--"+boundary+"--\r\n").getBytes(StandardCharsets.UTF_8));
        return http.send(HttpRequest.newBuilder(url("/api/documents/upload"))
                .header("Content-Type","multipart/form-data; boundary="+boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(bytes.toByteArray())).build(),HttpResponse.BodyHandlers.ofString());
    }
    byte[] pdf(String... pages) throws IOException {
        try(var document=new PDDocument(); var output=new ByteArrayOutputStream()) {
            for(String text:pages) {
                var page=new PDPage(); document.addPage(page);
                try(var content=new PDPageContentStream(document,page)) {
                    content.beginText(); content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA),12);
                    content.newLineAtOffset(40,700); content.showText(text); content.endText();
                }
            }
            document.save(output); return output.toByteArray();
        }
    }
    @Test void uploadPersistsPagesAndSearchReturnsCorrectSource() throws Exception {
        var result=upload("project.pdf",pdf("Deployment time reduced to five minutes.","Revenue increased by twenty percent."));
        assertEquals(201,result.statusCode(),result.body());
        var doc=store.list().stream().filter(d->d.originalFilename().equals("project.pdf")).findFirst().orElseThrow();
        assertEquals(2,doc.pageCount()); assertEquals(2,store.chunks(doc.id()).size());
        var found=get("/api/documents/"+doc.id()+"/search?q=revenue");
        assertEquals(200,found.statusCode()); assertTrue(found.body().contains("\"pageNumber\":2"));
        assertFalse(found.body().contains("Deployment"));
        assertEquals("[]",get("/api/documents/"+doc.id()+"/search?q=nonexistentword").body());
        var file=http.send(HttpRequest.newBuilder(url("/api/documents/"+doc.id()+"/pdf")).GET().build(),HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(200,file.statusCode()); assertEquals("%PDF",new String(file.body(),0,4,StandardCharsets.US_ASCII));
        assertEquals(200,get("/api/documents/"+doc.storedFilename()+"/chunks").statusCode());
    }
    @Test void malformedPdfDoesNotLeaveAFileOrDatabaseRow() throws Exception {
        int before=store.list().size(); long files;
        try(var paths=Files.list(uploads)) { files=paths.count(); }
        assertEquals(400,upload("broken.pdf","not a pdf".getBytes(StandardCharsets.UTF_8)).statusCode());
        assertEquals(before,store.list().size());
        try(var paths=Files.list(uploads)) { assertEquals(files,paths.count()); }
    }
    @Test void emptyScannedPdfIsRejected() throws Exception { assertEquals(422,upload("scan.pdf",pdf("")).statusCode()); }
    @Test void nonPdfAndEmptyFilesAreRejected() throws Exception {
        assertEquals(400,upload("file.txt",pdf("text")).statusCode());
        assertEquals(400,upload("empty.pdf",new byte[0]).statusCode());
    }
    @Test void documentSearchIsScopedAndValidatesInput() throws Exception {
        upload("first.pdf",pdf("alphaonly")); upload("second.pdf",pdf("betaonly"));
        var doc=store.list().stream().filter(d->d.originalFilename().equals("first.pdf")).findFirst().orElseThrow();
        assertEquals("[]",get("/api/documents/"+doc.id()+"/search?q=betaonly").body());
        assertEquals(400,get("/api/documents/"+doc.id()+"/search?q=%20").statusCode());
        assertEquals(404,get("/api/documents/missing/search?q=alpha").statusCode());
    }
    @Test void arbitraryFileNamesCannotBeRead() throws Exception { assertEquals(400,get("/api/documents/build.gradle/text").statusCode()); }
    @Test void homePageLoads() throws Exception { assertTrue(get("/").body().contains("문서 도우미")); }
    HttpResponse<String> ask(String id, String body) throws Exception {
        return http.send(HttpRequest.newBuilder(url("/api/documents/" + id + "/ask"))
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());
    }
    @Test void questionApiReturnsSourceAndHonestMode() throws Exception {
        upload("question.pdf", pdf("Deployment details.", "Revenue increased twenty percent."));
        var doc = store.list().stream().filter(d -> d.originalFilename().equals("question.pdf")).findFirst().orElseThrow();
        var response = ask(doc.id(), "{\"question\":\"How much revenue?\"}");
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("EXTRACTIVE"));
        assertTrue(response.body().contains("/pdf#page=2"));
        assertFalse(response.body().contains("Deployment details"));
        assertTrue(ask(doc.id(), "{\"question\":\"nonexistentword\"}").body().contains("NO_EVIDENCE"));
        assertEquals(400, ask(doc.id(), "{\"question\":\" \"}").statusCode());
        assertEquals(400, ask(doc.id(), "{}").statusCode());
        assertEquals(400, ask(doc.id(), "{broken").statusCode());
        assertEquals(404, ask("missing", "{\"question\":\"revenue\"}").statusCode());
    }
}
