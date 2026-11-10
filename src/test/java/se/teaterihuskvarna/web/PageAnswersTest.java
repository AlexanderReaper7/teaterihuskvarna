package se.teaterihuskvarna.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import gg.jte.TemplateOutput;
import gg.jte.models.runtime.JteModel;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.FlashMap;
import org.springframework.web.servlet.support.RequestContextUtils;
import org.springframework.web.servlet.support.SessionFlashMapManager;

/// The two answers a page handler gives, [HtmlPage] and [Redirect], and the
/// [Feedback] the next page reads.
class PageAnswersTest {

    @Test
    void aPageIsSentWholeWithItsLength() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        HtmlPage.of(page("<h1>Hushåll</h1>", null)).render(null, new MockHttpServletRequest(), response);

        assertThat(response.getContentType()).isEqualTo("text/html;charset=UTF-8");
        assertThat(response.getContentAsString(StandardCharsets.UTF_8)).isEqualTo("<h1>Hushåll</h1>");
        assertThat(response.getContentLength()).isEqualTo("<h1>Hushåll</h1>".getBytes(StandardCharsets.UTF_8).length);
    }

    /// Written straight to the response, the first half would have gone out
    /// with status 200 once it passed the buffer.
    @Test
    void aPageThatFailsHalfwaySendsNothing() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        HtmlPage failing = HtmlPage.of(page("<h1>Half", new IllegalStateException("the template failed")));

        assertThatThrownBy(() -> failing.render(null, new MockHttpServletRequest(), response))
                .isInstanceOf(IllegalStateException.class);

        assertThat(response.isCommitted()).isFalse();
        assertThat(response.getContentAsByteArray()).isEmpty();
        assertThat(response.getContentType()).isNull();
    }

    @Test
    void aRedirectCarriesItsNoticeToTheNextPagesFeedbackAndNothingElse() throws Exception {
        MockHttpServletRequest request = dispatched(new MockHttpServletRequest("POST", "/medlem/hushall"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        Redirect.to("/medlem").notice("Sparat").render(Map.of("form", "x", "count", 3), request, response);

        assertThat(response.getStatus()).isEqualTo(302);
        assertThat(response.getRedirectedUrl()).as("nothing from the model is added to the address")
                .isEqualTo("/medlem");
        MockHttpServletRequest next = new MockHttpServletRequest("GET", "/medlem");
        next.setAttribute(DispatcherServlet.INPUT_FLASH_MAP_ATTRIBUTE, RequestContextUtils.getOutputFlashMap(request));
        assertThat(Feedback.of(next)).isEqualTo(new Feedback(false, null, "Sparat"));
    }

    @Test
    void feedbackReadsAnErrorAndAStaleFormAndIsEmptyOtherwise() throws Exception {
        MockHttpServletRequest refused = dispatched(new MockHttpServletRequest("POST", "/medlem"));
        Redirect.to("/medlem").error("Fel").render(null, refused, new MockHttpServletResponse());
        MockHttpServletRequest next = new MockHttpServletRequest("GET", "/medlem");
        next.setAttribute(DispatcherServlet.INPUT_FLASH_MAP_ATTRIBUTE, RequestContextUtils.getOutputFlashMap(refused));
        next.setParameter("gammal", "");

        assertThat(Feedback.of(next)).isEqualTo(new Feedback(true, "Fel", null));
        assertThat(Feedback.of(new MockHttpServletRequest("GET", "/medlem")))
                .isEqualTo(new Feedback(false, null, null));
        assertThat(Feedback.of(next).withError("Annat")).isEqualTo(new Feedback(true, "Annat", null));
    }

    /// A request as the dispatcher hands it to a view, with somewhere to keep
    /// what a redirect carries.
    private static MockHttpServletRequest dispatched(MockHttpServletRequest request) {
        request.setAttribute(DispatcherServlet.OUTPUT_FLASH_MAP_ATTRIBUTE, new FlashMap());
        request.setAttribute(DispatcherServlet.FLASH_MAP_MANAGER_ATTRIBUTE, new SessionFlashMapManager());
        return request;
    }

    /// A page that writes `html` and then fails with `failure`, if there is one.
    private static JteModel page(String html, @Nullable RuntimeException failure) {
        JteModel page = mock(JteModel.class);
        doAnswer(call -> {
            call.<TemplateOutput>getArgument(0).writeContent(html);
            if (failure != null) {
                throw failure;
            }
            return null;
        }).when(page).render(any(TemplateOutput.class));
        return page;
    }
}
