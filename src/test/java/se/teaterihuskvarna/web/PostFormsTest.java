package se.teaterihuskvarna.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/// Every form in a template says how it is sent, one sent with `post` holds
/// the CSRF field, and the page it is on can say that it was refused.
///
/// Spring Security answers a post without the field with 403, and only when
/// someone presses the button, so a form no test presses would reach a
/// visitor broken. This reads the templates instead.
class PostFormsTest {

    private static final Path TEMPLATES = Path.of("src/main/jte");
    private static final Pattern FORM = Pattern.compile("<form\\b[^>]*>");
    private static final Pattern POSTED = Pattern.compile("<form\\b[^>]*method=\"post\"");
    private static final Pattern CALL = Pattern.compile("@template\\.([A-Za-z0-9_.]+)\\(");
    private static final String FIELD = "@template.tag.csrf(";

    @Test
    void everyPostFormHoldsTheCsrfField() throws IOException {
        Set<String> wrong = new TreeSet<>();
        int posted = 0;
        try (Stream<Path> files = Files.walk(Path.of("src/main/jte"))) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                String source = Files.readString(file);
                Matcher form = FORM.matcher(source);
                while (form.find()) {
                    String at = "%s line %d".formatted(file, source.substring(0, form.start()).lines().count() + 1);
                    if (form.group().contains("method=\"get\"")) {
                        continue;
                    }
                    if (!form.group().contains("method=\"post\"")) {
                        wrong.add(at + " does not say method=\"get\" or method=\"post\"");
                        continue;
                    }
                    posted++;
                    int end = source.indexOf("</form>", form.end());
                    if (end < 0 || !source.substring(form.end(), end).contains(FIELD)) {
                        wrong.add(at + " is posted without " + FIELD + "csrf = csrf)");
                    }
                }
            }
        }

        assertThat(wrong).isEmpty();
        assertThat(posted).as("forms read, so the test cannot pass by finding none").isGreaterThan(30);
    }

    /// A form whose session is gone is refused and sent back to the page it
    /// was on, with `gammal` in the query, and a [Feedback] reads that. A page
    /// that takes none shows the form again and says nothing, as six pages
    /// did. The form may be the page's own or one a tag draws for it, as
    /// `tag/offerForm.jte` does.
    @Test
    void everyPageWithAPostedFormShowsItsFeedback() throws IOException {
        Map<String, String> sources = new TreeMap<>();
        try (Stream<Path> files = Files.walk(TEMPLATES)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                sources.put(TEMPLATES.relativize(file).toString().replace('\\', '/'), Files.readString(file));
            }
        }
        Set<String> wrong = new TreeSet<>();
        int pages = 0;
        for (Map.Entry<String, String> template : sources.entrySet()) {
            if (!isPage(template.getKey()) || !posts(template.getKey(), sources, new HashSet<>())) {
                continue;
            }
            pages++;
            String source = template.getValue();
            if (!source.contains("@param Feedback feedback") || !source.contains("@template.tag.feedback(")) {
                wrong.add(template.getKey() + " posts a form and does not show a Feedback");
            }
        }

        assertThat(wrong).isEmpty();
        assertThat(pages).as("pages read, so the test cannot pass by finding none").isGreaterThan(25);
    }

    /// A template a handler shows, and not a part of one: a tag, an icon, a
    /// layout or a mail.
    private static boolean isPage(String template) {
        return !template.startsWith("tag/") && !template.startsWith("icon/") && !template.startsWith("mail/")
                && !template.endsWith("layout.jte");
    }

    /// @return whether the template holds a posted form, or calls a template that does
    private static boolean posts(String template, Map<String, String> sources, Set<String> seen) {
        String source = sources.get(template);
        if (source == null || !seen.add(template)) {
            return false;
        }
        if (POSTED.matcher(source).find()) {
            return true;
        }
        Matcher call = CALL.matcher(source);
        while (call.find()) {
            if (posts(call.group(1).replace('.', '/') + ".jte", sources, seen)) {
                return true;
            }
        }
        return false;
    }
}
