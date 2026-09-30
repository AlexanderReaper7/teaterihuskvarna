package se.teaterihuskvarna.content;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import se.teaterihuskvarna.IntegrationTestSupport;

/// R009 through the REST API: the Studio's secret buys a pass, and the pass in
/// the `Preview-Pass` header makes the content API read drafts.
///
/// The fixture shows drafts as the published documents, so the response alone
/// cannot tell the two apart. The test watches which perspective the content
/// source is asked for instead.
class ContentPreviewApiIT extends IntegrationTestSupport {

    @MockitoSpyBean
    private ContentSource source;

    @Test
    void aSecretSanityKnowsBuysAPassThatReadsDrafts() throws Exception {
        doReturn(true).when(source).previewSecretValid("hemlig");

        String body = mockMvc.perform(post("/api/content/preview").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"secret\": \"hemlig\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expiresAt").isString())
                .andReturn().getResponse().getContentAsString();
        String pass = JsonPath.read(body, "$.value");

        clearInvocations(source);
        mockMvc.perform(get("/api/content/news").header("Preview-Pass", pass)).andExpect(status().isOk());
        verify(source, atLeastOnce()).documents(anyString(), eq(Perspective.DRAFTS));

        clearInvocations(source);
        mockMvc.perform(get("/api/content/news").header("Preview-Pass", "99999999999.AAAA"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/content/news")).andExpect(status().isOk());
        verify(source, never()).documents(anyString(), eq(Perspective.DRAFTS));
    }

    @Test
    void aSecretSanityDoesNotKnowIsRefused() throws Exception {
        mockMvc.perform(post("/api/content/preview").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"secret\": \"gissat\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/content/preview").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void bothEntryPointsShareTheClientAllowanceAndStopQueryingSanity() throws Exception {
        String client = "192.0.2.10";
        for (int attempt = 0; attempt < 19; attempt++) {
            mockMvc.perform(post("/api/content/preview").with(csrf())
                            .with(clientAddress(client))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"secret\":\"wrong\"}"))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(get("/forhandsgranska/start").param("sanity-preview-secret", "wrong")
                        .with(clientAddress(client)))
                .andExpect(status().isFound());

        mockMvc.perform(post("/api/content/preview").with(csrf())
                        .with(clientAddress(client))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"secret\":\"wrong\"}"))
                .andExpect(status().isTooManyRequests());
        mockMvc.perform(get("/forhandsgranska/start").param("sanity-preview-secret", "wrong")
                        .with(clientAddress(client)))
                .andExpect(status().isTooManyRequests());
        verify(source, times(20)).previewSecretValid("wrong");

        doReturn(true).when(source).previewSecretValid("valid");
        mockMvc.perform(get("/forhandsgranska/start").param("sanity-preview-secret", "valid")
                        .param("sanity-preview-pathname", "/nyheter")
                        .with(clientAddress("192.0.2.11")))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "/nyheter"))
                .andExpect(header().exists("Set-Cookie"));
    }

    private static RequestPostProcessor clientAddress(String address) {
        return request -> {
            request.setRemoteAddr(address);
            return request;
        };
    }
}
