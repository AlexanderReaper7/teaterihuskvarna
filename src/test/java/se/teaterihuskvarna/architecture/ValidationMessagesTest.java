package se.teaterihuskvarna.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import jakarta.validation.Constraint;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Executable;
import java.lang.reflect.Member;
import java.lang.reflect.Parameter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.ConfigurationProperties;

/// Every constraint on what a person or a client sends names its message by a
/// key in `messages_sv.properties`, as `docs/decisions/0001-language-policy.md`
/// requires of a validation message.
///
/// A constraint without a key gets the validator's own text. Hibernate
/// Validator 9.1.5 has none in Swedish, so that text is English, "must not be
/// blank", and before the locale was fixed it followed the browser: the
/// mailing form answered a German one "darf nicht leer sein".
///
/// Checks the constraints on fields, methods and parameters, which is where a
/// record's components put theirs. A constraint inside a type argument, such
/// as `List<@NotBlank String>`, is not checked, and the code has none.
///
/// A `@ConfigurationProperties` class is left out. Its constraints are read
/// at startup by whoever runs the application, in a log that is in English.
class ValidationMessagesTest {

    private static final Pattern KEY = Pattern.compile("\\{([A-Za-z0-9_.]+)}");

    @Test
    void everyConstraintNamesAKeyInTheSwedishMessages() throws Exception {
        Properties messages = new Properties();
        try (InputStream file = getClass().getResourceAsStream("/messages_sv.properties")) {
            assertThat(file).as("messages_sv.properties on the class path").isNotNull();
            messages.load(new InputStreamReader(file, StandardCharsets.UTF_8));
        }

        Set<String> constraints = new TreeSet<>();
        Set<String> withoutAKey = new TreeSet<>();
        for (JavaClass type : new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("se.teaterihuskvarna")) {
            Class<?> owner = type.reflect();
            if (owner.isAnnotationPresent(ConfigurationProperties.class)) {
                continue;
            }
            for (AnnotatedElement element : annotated(owner)) {
                for (Annotation annotation : element.getDeclaredAnnotations()) {
                    if (!annotation.annotationType().isAnnotationPresent(Constraint.class)) {
                        continue;
                    }
                    String message = (String) annotation.annotationType().getMethod("message").invoke(annotation);
                    String constraint = owner.getName() + "." + name(element) + " @"
                            + annotation.annotationType().getSimpleName();
                    constraints.add(constraint);
                    Matcher key = KEY.matcher(message);
                    if (!key.matches() || !messages.containsKey(key.group(1))) {
                        withoutAKey.add(constraint);
                    }
                }
            }
        }

        assertThat(constraints).as("the constraints this test found, which must not be none").isNotEmpty();
        assertThat(withoutAKey)
                .as("constraints whose message is not {a key in messages_sv.properties}")
                .isEmpty();
    }

    /// A record component's constraint is on its field, its accessor and its
    /// constructor parameter, which all have the component's name.
    ///
    /// @param element a field, a method, a constructor or a parameter
    /// @return its name
    private static String name(AnnotatedElement element) {
        return switch (element) {
            case Member member -> member.getName();
            case Parameter parameter -> parameter.getName();
            default -> element.toString();
        };
    }

    /// @param owner a class
    /// @return its fields, methods and constructors, and the parameters of both
    private static List<AnnotatedElement> annotated(Class<?> owner) {
        List<AnnotatedElement> elements = new ArrayList<>(List.of(owner.getDeclaredFields()));
        List<Executable> executables = new ArrayList<>(List.of(owner.getDeclaredMethods()));
        executables.addAll(List.of(owner.getDeclaredConstructors()));
        for (Executable executable : executables) {
            elements.add(executable);
            for (Parameter parameter : executable.getParameters()) {
                elements.add(parameter);
            }
        }
        return elements;
    }
}
