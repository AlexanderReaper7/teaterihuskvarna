package se.teaterihuskvarna.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import se.teaterihuskvarna.IntegrationTestSupport;
import se.teaterihuskvarna.login.LoginKind;
import se.teaterihuskvarna.login.SignedIn;
import se.teaterihuskvarna.web.Copy;

/// Proves the member document rules (R015) through both adapters: the
/// `/medlem` and `/admin` pages and the REST API.
///
/// - Only a member or an administrator downloads a document, and only an
///   administrator uploads or deletes one.
/// - An upload must be a PDF by its bytes, whatever type it claims, and at most
///   10 MB. Its file name loses any path, quote or control character.
/// - A download is an attachment with an ASCII and a UTF-8 name, `nosniff` and
///   `no-store`, the same from all four paths.
/// - A list never carries the file.
class MemberDocumentIT extends IntegrationTestSupport {

    private static final byte[] PDF = "%PDF-1.4\n%test\n".getBytes(StandardCharsets.US_ASCII);

    @Autowired
    private MemberDocumentService documents;

    @Autowired
    private Copy copy;

    // Who reaches what

    @Test
    void anonymousRequestsAreSentToLogInOrRefused() throws Exception {
        long id = insertDocument("Kallelse", PDF);

        assertRedirect(mockMvc.perform(get("/medlem/handlingar")).andReturn(), "/logga-in");
        assertRedirect(mockMvc.perform(get("/medlem/handlingar/" + id + "/fil")).andReturn(), "/logga-in");
        assertRedirect(mockMvc.perform(get("/admin/handlingar")).andReturn(), "/admin/logga-in");
        assertRedirect(mockMvc.perform(get("/admin/handlingar/" + id + "/fil")).andReturn(), "/admin/logga-in");
        assertRedirect(mockMvc.perform(multipart("/admin/handlingar").file(pdf("a.pdf", PDF))
                .param("title", "Smyg").param("kind", "MEMBER_LETTER").param("publishedOn", "2026-09-01")
                .with(csrf())).andReturn(), "/admin/logga-in");
        mockMvc.perform(get("/api/member/documents")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/member/documents/" + id + "/file")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/documents")).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/admin/documents/" + id).with(csrf())).andExpect(status().isUnauthorized());

        assertThat(rowsIn("member_document")).isEqualTo(1);
    }

    @Test
    void aMemberDownloadsButCannotReachTheAdministratorSide() throws Exception {
        long id = insertDocument("Kallelse", PDF);
        RequestPostProcessor member = asMember(insertAccount("Karin Holm", "karin@example.test"));

        mockMvc.perform(get("/medlem/handlingar/" + id + "/fil").with(member)).andExpect(status().isOk());
        mockMvc.perform(get("/api/member/documents/" + id + "/file").with(member)).andExpect(status().isOk());

        assertRedirect(mockMvc.perform(get("/admin/handlingar").with(member)).andReturn(), "/admin/logga-in");
        assertRedirect(mockMvc.perform(get("/admin/handlingar/" + id + "/fil").with(member)).andReturn(),
                "/admin/logga-in");
        assertRedirect(mockMvc.perform(post("/admin/handlingar/" + id + "/ta-bort").with(member).with(csrf()))
                .andReturn(), "/admin/logga-in");
        mockMvc.perform(get("/api/admin/documents").with(member)).andExpect(status().isForbidden());
        mockMvc.perform(multipart("/api/admin/documents").file(pdf("a.pdf", PDF)).param("title", "Smyg")
                .param("kind", "MEMBER_LETTER").param("publishedOn", "2026-09-01").with(member).with(csrf()))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/admin/documents/" + id).with(member).with(csrf()))
                .andExpect(status().isForbidden());

        assertThat(rowsIn("member_document")).isEqualTo(1);
    }

    // Uploading

    @Test
    void theAdministratorPageUploadsAPdfThatMembersThenSee() throws Exception {
        RequestPostProcessor member = asMember(insertAccount("Karin Holm", "karin@example.test"));

        mockMvc.perform(multipart("/admin/handlingar").file(pdf("Kallelse årsmöte.pdf", PDF))
                        .param("title", "Kallelse till årsmötet").param("kind", "ANNUAL_MEETING")
                        .param("publishedOn", "2026-03-01").with(asAdministrator()).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("notice",
                        copy.text("adminDocuments.uploaded", "Kallelse till årsmötet")));

        assertThat(jdbc.sql("SELECT filename FROM member_document").query(String.class).single())
                .isEqualTo("Kallelse årsmöte.pdf");
        assertThat(jdbc.sql("SELECT uploaded_by FROM member_document").query(Long.class).single())
                .isEqualTo(firstAdministratorId());
        mockMvc.perform(get("/medlem/handlingar").with(member))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Kallelse till årsmötet")))
                .andExpect(content().string(containsString(copy.text("document.kind.ANNUAL_MEETING.plural"))));
    }

    @Test
    void theApiUploadsAPdfAndListsItWithoutTheFile() throws Exception {
        mockMvc.perform(multipart("/api/admin/documents").file(pdf("brev.pdf", PDF))
                        .param("title", "Medlemsbrev september").param("kind", "MEMBER_LETTER")
                        .param("publishedOn", "2026-09-01").with(asAdministrator()).with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Medlemsbrev september"))
                .andExpect(jsonPath("$.kind").value("MEMBER_LETTER"))
                .andExpect(jsonPath("$.filename").value("brev.pdf"))
                .andExpect(jsonPath("$.sizeBytes").value(PDF.length))
                .andExpect(jsonPath("$", not(hasKey("content"))));

        RequestPostProcessor member = asMember(insertAccount("Karin Holm", "karin@example.test"));
        mockMvc.perform(get("/api/member/documents").with(member))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Medlemsbrev september"))
                .andExpect(jsonPath("$[0]", not(hasKey("content"))));
        mockMvc.perform(get("/api/admin/documents").with(asAdministrator()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]", not(hasKey("content"))));
    }

    @Test
    void aFileThatIsNotAPdfIsRefusedWhateverTypeItClaims() throws Exception {
        MockMultipartFile html = new MockMultipartFile("file", "brev.pdf", "application/pdf",
                "<html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/admin/handlingar").file(html).param("title", "Brev")
                        .param("kind", "MEMBER_LETTER").param("publishedOn", "2026-09-01")
                        .with(asAdministrator()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(copy.text("adminDocuments.error.notPdf"))));
        mockMvc.perform(multipart("/api/admin/documents").file(html).param("title", "Brev")
                        .param("kind", "MEMBER_LETTER").param("publishedOn", "2026-09-01")
                        .with(asAdministrator()).with(csrf()))
                .andExpect(status().isBadRequest());
        mockMvc.perform(multipart("/api/admin/documents").param("title", "Brev")
                        .param("kind", "MEMBER_LETTER").param("publishedOn", "2026-09-01")
                        .with(asAdministrator()).with(csrf()))
                .andExpect(status().isBadRequest());

        assertThat(rowsIn("member_document")).isZero();
    }

    @Test
    void aFileOverTenMegabytesIsRefused() throws Exception {
        byte[] large = Arrays.copyOf(PDF, MemberDocumentService.MAX_BYTES + 1);
        DocumentForm form = new DocumentForm("Stor", DocumentKind.MEMBER_LETTER, LocalDate.of(2026, 9, 1));

        assertThatThrownBy(() -> documents.upload(form, "stor.pdf", large, firstAdministratorId()))
                .isInstanceOf(FileTooLarge.class);
        byte[] limit = Arrays.copyOf(PDF, MemberDocumentService.MAX_BYTES);
        assertThat(documents.upload(form, "precis.pdf", limit, firstAdministratorId()).sizeBytes())
                .isEqualTo(MemberDocumentService.MAX_BYTES);
        assertThat(rowsIn("member_document")).isEqualTo(1);
    }

    @Test
    void theUploadFormsSayWhatIsMissing() throws Exception {
        mockMvc.perform(multipart("/admin/handlingar").file(pdf("a.pdf", PDF)).param("title", " ")
                        .param("kind", "").param("publishedOn", "").with(asAdministrator()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(copy.text("document.title.required"))))
                .andExpect(content().string(containsString(copy.text("document.kind.required"))))
                .andExpect(content().string(containsString(copy.text("document.publishedOn.required"))))
                .andExpect(content().string(containsString("aria-invalid=\"true\"")));
        mockMvc.perform(multipart("/api/admin/documents").file(pdf("a.pdf", PDF)).param("title", "")
                        .with(asAdministrator()).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.title").value(copy.text("document.title.required")))
                .andExpect(jsonPath("$.errors.kind").value(copy.text("document.kind.required")))
                .andExpect(jsonPath("$.errors.publishedOn").value(copy.text("document.publishedOn.required")));

        assertThat(rowsIn("member_document")).isZero();
    }

    @Test
    void theUploadFormKeepsItsTokenOutOfTheAddress() throws Exception {
        mockMvc.perform(get("/admin/handlingar").with(asAdministrator()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "action=\"/admin/handlingar\" enctype=\"multipart/form-data\"")));
    }

    /// A body over the limit is stopped before anything reads it: the page
    /// says the file is too large, the API answers 413. A small upload without
    /// a token is still a stale form. Only the e2e suite shows what Tomcat
    /// would do without the filter, since MockMvc parses no multipart body.
    @Test
    void anUploadOverTheLimitIsTooLargeRatherThanStale() throws Exception {
        byte[] body = new byte[11 * 1024 * 1024 + 1];
        assertRedirect(mockMvc.perform(post("/admin/handlingar").with(asAdministrator())
                        .header("Referer", "http://localhost/admin/handlingar")
                        .contentType("multipart/form-data; boundary=x")
                        .content(body))
                .andReturn(), "/admin/handlingar?forstor");
        mockMvc.perform(post("/api/admin/documents").with(asAdministrator())
                        .contentType("multipart/form-data; boundary=x")
                        .content(body))
                .andExpect(status().isContentTooLarge());
        mockMvc.perform(get("/admin/handlingar").param("forstor", "").with(asAdministrator()))
                .andExpect(content().string(containsString(copy.text("adminDocuments.error.tooLarge"))));
        assertRedirect(mockMvc.perform(post("/admin/handlingar").with(asAdministrator())
                        .header("Referer", "http://localhost/admin/handlingar")
                        .contentType("multipart/form-data; boundary=x")
                        .content(new byte[100]))
                .andReturn(), "/admin/handlingar?gammal");
        assertThat(rowsIn("member_document")).isZero();
    }

    // Downloading

    @Test
    void everyDownloadPathSendsTheSameSafeHeaders() throws Exception {
        long id = documents.upload(new DocumentForm("Brev", DocumentKind.MEMBER_LETTER, LocalDate.of(2026, 9, 1)),
                "C:\\hem\\..\\Års\"möte\r\n.pdf", PDF, firstAdministratorId()).id();
        RequestPostProcessor member = asMember(insertAccount("Karin Holm", "karin@example.test"));

        List<MvcResult> downloads = List.of(
                mockMvc.perform(get("/medlem/handlingar/" + id + "/fil").with(member)).andReturn(),
                mockMvc.perform(get("/api/member/documents/" + id + "/file").with(member)).andReturn(),
                mockMvc.perform(get("/admin/handlingar/" + id + "/fil").with(asAdministrator())).andReturn(),
                mockMvc.perform(get("/api/admin/documents/" + id + "/file").with(asAdministrator())).andReturn());

        for (MvcResult download : downloads) {
            assertThat(download.getResponse().getStatus()).isEqualTo(200);
            assertThat(download.getResponse().getContentType()).isEqualTo("application/pdf");
            assertThat(download.getResponse().getHeader("Content-Disposition"))
                    .isEqualTo("attachment; filename=\"Arsmote.pdf\"; filename*=UTF-8''%C3%85rsm%C3%B6te.pdf");
            assertThat(download.getResponse().getHeaders("X-Content-Type-Options")).containsExactly("nosniff");
            assertThat(download.getResponse().getHeader("Cache-Control")).contains("no-store").contains("private");
            assertThat(download.getResponse().getContentAsByteArray()).isEqualTo(PDF);
        }
    }

    @Test
    void aMissingDocumentIsNotFound() throws Exception {
        RequestPostProcessor member = asMember(insertAccount("Karin Holm", "karin@example.test"));

        mockMvc.perform(get("/medlem/handlingar/999999/fil").with(member)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/member/documents/999999/file").with(member)).andExpect(status().isNotFound());
        mockMvc.perform(get("/admin/handlingar/999999/fil").with(asAdministrator()))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/admin/documents/999999").with(asAdministrator()).with(csrf()))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/admin/handlingar/999999/ta-bort").with(asAdministrator()).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("error", copy.text("adminDocuments.error.noSuch")));
    }

    // Deleting

    @Test
    void anAdministratorDeletesADocumentThroughEitherAdapter() throws Exception {
        long first = insertDocument("Första", PDF);
        long second = insertDocument("Andra", PDF);

        mockMvc.perform(post("/admin/handlingar/" + first + "/ta-bort").with(asAdministrator()).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("notice", copy.text("adminDocuments.deleted")));
        mockMvc.perform(delete("/api/admin/documents/" + second).with(asAdministrator()).with(csrf()))
                .andExpect(status().isNoContent());

        assertThat(rowsIn("member_document")).isZero();
        mockMvc.perform(get("/admin/handlingar").with(asAdministrator()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(copy.text("adminDocuments.none"))));
    }

    @Test
    void theListIsGroupedByKindNewestFirst() {
        insertDocument("Gammalt brev", DocumentKind.MEMBER_LETTER, LocalDate.of(2025, 1, 1));
        insertDocument("Nytt brev", DocumentKind.MEMBER_LETTER, LocalDate.of(2026, 1, 1));
        insertDocument("Kallelse", DocumentKind.ANNUAL_MEETING, LocalDate.of(2024, 1, 1));

        assertThat(documents.list()).extracting(DocumentSummary::title)
                .containsExactly("Kallelse", "Nytt brev", "Gammalt brev");
    }

    // Helpers

    private long insertDocument(String title, byte[] content) {
        return documents.upload(new DocumentForm(title, DocumentKind.MEMBER_LETTER, LocalDate.of(2026, 9, 1)),
                title + ".pdf", content, firstAdministratorId()).id();
    }

    private void insertDocument(String title, DocumentKind kind, LocalDate publishedOn) {
        documents.upload(new DocumentForm(title, kind, publishedOn), title + ".pdf", PDF, firstAdministratorId());
    }

    private static MockMultipartFile pdf(String filename, byte[] content) {
        return new MockMultipartFile("file", filename, "application/pdf", content);
    }

    private RequestPostProcessor asMember(long accountId) {
        String email = jdbc.sql("SELECT email FROM account WHERE id = ?").param(accountId).query(String.class)
                .single();
        return user(new SignedIn(LoginKind.MEMBER, accountId, email, "Medlem"));
    }

    private RequestPostProcessor asAdministrator() {
        return user(new SignedIn(LoginKind.ADMINISTRATOR, firstAdministratorId(), firstAdministratorEmail,
                "Ada Admin"));
    }
}
