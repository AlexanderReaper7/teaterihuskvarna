package se.teaterihuskvarna.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/// Every key a template or a class gives to `copy.text` is in
/// `messages_sv.properties`.
///
/// [Copy] throws on a key that is missing, and a page only asks for a key when
/// it draws the branch the key is in, so a misspelt key in an error message is
/// an exception for the one person who meets that error. This reads the
/// sources instead, so every branch is checked.
///
/// It checks the keys written out as text, alone or as the two sides of a
/// `? :`. A key put together while the page is drawn, as `"shift.task." +
/// shift.task()` is, cannot be read from the source, so the second test checks
/// those from the other side: where the keys under such a start name some of
/// the constants of an enum, they name all of them.
class CopyKeysTest {

    private static final Pattern CALL = Pattern.compile("copy\\.text\\(");
    private static final Pattern TEXT = Pattern.compile("\"([^\"]*)\"");
    private static final Pattern START = Pattern.compile("^\\s*\"([A-Za-z0-9.]+\\.)\"\\s*\\+");

    @Test
    void everyKeyWrittenOutIsInTheSwedishMessages() throws IOException {
        Set<String> keys = keys();
        Set<String> missing = new TreeSet<>();
        int read = 0;
        for (Use use : uses()) {
            if (use.key().contains("+")) {
                continue;
            }
            Matcher text = TEXT.matcher(use.key());
            while (text.find()) {
                read++;
                if (!keys.contains(text.group(1))) {
                    missing.add("%s asks for %s".formatted(use.file(), text.group(1)));
                }
            }
        }

        assertThat(missing).as("keys missing from messages_sv.properties").isEmpty();
        assertThat(read).as("keys read, so the test cannot pass by finding none").isGreaterThan(500);
    }

    @Test
    void aKeyThatEndsInAnEnumConstantExistsForEveryConstant() throws IOException {
        Set<String> keys = keys();
        Set<String> missing = new TreeSet<>();
        Set<String> starts = new TreeSet<>();
        for (Use use : uses()) {
            Matcher start = START.matcher(use.key());
            if (start.find()) {
                starts.add(start.group(1));
            }
        }
        int compared = 0;
        for (String start : starts) {
            Set<String> ends = new HashSet<>();
            for (String key : keys) {
                if (key.startsWith(start)) {
                    ends.add(key.substring(start.length()));
                }
            }
            for (Class<?> type : enums()) {
                List<String> constants = Arrays.stream(type.getEnumConstants()).map(Object::toString).toList();
                if (constants.stream().noneMatch(ends::contains)) {
                    continue;
                }
                compared++;
                for (String constant : constants) {
                    if (!ends.contains(constant)) {
                        missing.add("%s%s for %s.%s".formatted(start, constant, type.getSimpleName(), constant));
                    }
                }
            }
        }

        assertThat(missing).as("keys missing from messages_sv.properties").isEmpty();
        assertThat(compared).as("enums compared, so the test cannot pass by finding none").isGreaterThan(2);
    }

    /// The first argument of every `copy.text(` in the templates and the classes.
    private static List<Use> uses() throws IOException {
        List<Use> uses = new ArrayList<>();
        for (String root : new String[] {"src/main/jte", "src/main/java"}) {
            try (Stream<Path> files = Files.walk(Path.of(root))) {
                for (Path file : files.filter(Files::isRegularFile).toList()) {
                    String source = Files.readString(file);
                    Matcher call = CALL.matcher(source);
                    while (call.find()) {
                        uses.add(new Use(file.toString(), firstArgument(source, call.end())));
                    }
                }
            }
        }
        return uses;
    }

    /// From `from` to the comma or the bracket that ends the argument, past any
    /// text and any brackets inside it.
    private static String firstArgument(String source, int from) {
        int depth = 0;
        boolean text = false;
        for (int i = from; i < source.length(); i++) {
            char c = source.charAt(i);
            if (text) {
                if (c == '\\') {
                    i++;
                } else if (c == '"') {
                    text = false;
                }
            } else if (c == '"') {
                text = true;
            } else if (c == '(') {
                depth++;
            } else if (c == ')' && depth-- == 0 || c == ',' && depth == 0) {
                return source.substring(from, i);
            }
        }
        return source.substring(from);
    }

    private Set<String> keys() throws IOException {
        Properties messages = new Properties();
        try (InputStream file = getClass().getResourceAsStream("/messages_sv.properties")) {
            assertThat(file).as("messages_sv.properties on the class path").isNotNull();
            messages.load(new InputStreamReader(file, StandardCharsets.UTF_8));
        }
        return messages.stringPropertyNames();
    }

    private static List<Class<?>> enums() {
        List<Class<?>> enums = new ArrayList<>();
        for (JavaClass type : new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("se.teaterihuskvarna")) {
            if (type.isEnum()) {
                enums.add(type.reflect());
            }
        }
        return enums;
    }

    private record Use(String file, String key) {
    }
}
