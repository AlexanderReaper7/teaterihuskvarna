package se.teaterihuskvarna.login;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Locale;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

/// Stops a file upload that is over the limit before anything reads its body.
///
/// Spring Security's `CsrfFilter` reads the token with `getParameter`, which
/// makes Tomcat parse the multipart body. Past `spring.servlet.multipart`'s
/// limit Tomcat gives up and answers 413 itself, with no page, so the person
/// saw a bare error. Measured with curl against the e2e stack on 2026-09-29.
/// This filter runs first and, going by the `Content-Length` alone, sends a
/// request over `max-request-size` back to its page to say the file is too
/// large ([RefusedRequests]). A smaller request is parsed, and the document
/// service applies the exact 10 MB limit to the file itself.
final class OversizeUploadFilter extends OncePerRequestFilter {

    private final RefusedRequests refused;
    private final long largestRequest;

    /// @param refused        answers the upload
    /// @param largestRequest the most bytes an upload request may have
    OversizeUploadFilter(RefusedRequests refused, long largestRequest) {
        this.refused = refused;
        this.largestRequest = largestRequest;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String type = request.getContentType();
        boolean multipart = type != null
                && type.toLowerCase(Locale.ROOT).startsWith(MediaType.MULTIPART_FORM_DATA_VALUE);
        if (multipart && request.getContentLengthLong() > largestRequest) {
            refused.uploadTooLarge(request, response);
            return;
        }
        chain.doFilter(request, response);
    }
}
