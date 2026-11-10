package se.teaterihuskvarna.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

/// Every path a template links to, posts to or loads is one a page controller
/// maps, or a file under `static`.
///
/// A template writes its paths out as text, and nothing ties them to the
/// mappings, so a misspelt one is a 404 for whoever clicks it. Only a test
/// that clicks that very link would notice. This reads every template.
///
/// A path that starts with an expression, as `${urls.status()}` does, is not
/// read: its text is not in the template.
class TemplateLinksTest {

    /// What answers these is neither a page controller nor a file in the
    /// source tree: Spring Security's filter for the development login, and
    /// the two files the Dockerfile's `visual-editing` stage builds.
    private static final Set<String> ELSEWHERE = Set.of(
            "POST /dev/login", "GET /css/visual-editing.css", "GET /js/visual-editing.js");

    private static final Pattern TAG = Pattern.compile("<(form|a|link|script|img)\\b[^>]*>");
    private static final Pattern PATH = Pattern.compile("\\b(?:href|action|src)=\"(/[^\"]*)\"");

    @Test
    void everyPathInATemplateLeadsSomewhere() throws IOException {
        List<Pattern> gets = new ArrayList<>();
        List<Pattern> posts = new ArrayList<>();
        for (JavaClass controller : new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("se.teaterihuskvarna.web")) {
            for (Method method : controller.reflect().getDeclaredMethods()) {
                RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
                if (mapping == null) {
                    continue;
                }
                for (String path : mapping.path()) {
                    Pattern pattern = Pattern.compile(path.replaceAll("\\{[^}]+}", "[^/]+"));
                    List<RequestMethod> methods = List.of(mapping.method());
                    if (methods.isEmpty() || methods.contains(RequestMethod.GET)) {
                        gets.add(pattern);
                    }
                    if (methods.isEmpty() || methods.contains(RequestMethod.POST)) {
                        posts.add(pattern);
                    }
                }
            }
        }

        Set<String> lost = new TreeSet<>();
        int read = 0;
        try (Stream<Path> files = Files.walk(Path.of("src/main/jte"))) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                Matcher tag = TAG.matcher(Files.readString(file));
                while (tag.find()) {
                    Matcher written = PATH.matcher(tag.group());
                    if (!written.find()) {
                        continue;
                    }
                    read++;
                    // A value drawn into the path stands for one segment, and a query is not part of it.
                    String path = written.group(1).replaceAll("\\$\\{[^}]*}", "1").split("[?#]")[0];
                    boolean post = tag.group(1).equals("form") && tag.group().contains("method=\"post\"");
                    boolean mapped = (post ? posts : gets).stream().anyMatch(known -> known.matcher(path).matches());
                    boolean isAFile = !post && Files.isRegularFile(Path.of("src/main/resources/static" + path));
                    String request = (post ? "POST " : "GET ") + path;
                    if (!mapped && !isAFile && !ELSEWHERE.contains(request)) {
                        lost.add("%s has %s".formatted(file, request));
                    }
                }
            }
        }

        assertThat(lost).as("paths in templates that no page controller maps and no static file is").isEmpty();
        assertThat(read).as("paths read, so the test cannot pass by finding none").isGreaterThan(100);
    }
}
