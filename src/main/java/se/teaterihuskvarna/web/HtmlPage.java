package se.teaterihuskvarna.web;

import gg.jte.models.runtime.JteModel;
import gg.jte.output.Utf8ByteOutput;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.web.servlet.View;

/// A page, as a handler answers with it: one of the methods on
/// `gg.jte.generated.precompiled.Templates`, called with what the page shows.
///
/// `HtmlPage.of(templates.memberHousehold(copy, csrf, ..))` is what
/// `model.addAttribute(..)` and `return "member/household"` were. The method
/// exists only while the template does, and takes the template's `@param`
/// lines as its parameters, so a page that is not there, a value left out
/// and a value of the wrong type fail the compile. Before, the first two drew
/// a page with a hole in it and the third failed when the page was asked for.
///
/// The page is drawn in full before any of it is sent. A template that fails
/// halfway therefore answers with the error page, where writing straight to
/// the response sent half a page with status 200 once the first 8 kB had gone.
public final class HtmlPage implements View {

    private static final String CONTENT_TYPE = "text/html;charset=UTF-8";

    private final JteModel page;

    private HtmlPage(JteModel page) {
        this.page = page;
    }

    /// @param page a call to one of the methods on `Templates`
    /// @return the page as an answer
    public static HtmlPage of(JteModel page) {
        return new HtmlPage(page);
    }

    @Override
    public String getContentType() {
        return CONTENT_TYPE;
    }

    @Override
    public void render(@Nullable Map<String, ?> model, HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        Utf8ByteOutput output = new Utf8ByteOutput();
        page.render(output);
        response.setContentType(CONTENT_TYPE);
        response.setContentLength(output.getContentLength());
        output.writeTo(response.getOutputStream());
    }
}
