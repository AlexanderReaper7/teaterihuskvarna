package se.teaterihuskvarna.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/// Every form in a template says how it is sent, and one sent with `post`
/// holds the CSRF field.
///
/// Spring Security answers a post without the field with 403, and only when
/// someone presses the button, so a form no test presses would reach a
/// visitor broken. This reads the templates instead.
class PostFormsTest {

    private static final Pattern FORM = Pattern.compile("<form\\b[^>]*>");
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
}
