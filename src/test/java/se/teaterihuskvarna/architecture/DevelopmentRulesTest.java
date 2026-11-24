package se.teaterihuskvarna.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestMapping;

/// Every development tool is a bean of the `dev` profile only.
///
/// The tools log in as anyone without a mail and list every account, and the
/// security configuration permits their paths in every profile, so
/// `@Profile("dev")` on each class is all that keeps them out of production.
/// `LoginIT` asks for four paths it names and expects 404. This finds the
/// classes instead, so a new tool is checked without being listed.
///
/// A development tool is a bean in the `development` or the `seed` package, a
/// bean whose name starts with `Dev` or `Development`, and a bean with a
/// mapping under `/dev` or `/api/development`.
class DevelopmentRulesTest {

    @Test
    void everyDevelopmentToolIsABeanOfTheDevProfileOnly() {
        Set<String> tools = new TreeSet<>();
        Set<String> open = new TreeSet<>();
        for (JavaClass imported : new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("se.teaterihuskvarna")) {
            Class<?> type = imported.reflect();
            if (!AnnotatedElementUtils.hasAnnotation(type, Component.class) || !isATool(type)) {
                continue;
            }
            tools.add(type.getSimpleName());
            Profile profile = type.getAnnotation(Profile.class);
            if (profile == null || !Arrays.equals(profile.value(), new String[] {"dev"})) {
                open.add(type.getName());
            }
        }

        assertThat(open).as("development tools without @Profile(\"dev\")").isEmpty();
        assertThat(tools).as("the tools found, so the test cannot pass by finding none").hasSizeGreaterThan(5);
    }

    private static boolean isATool(Class<?> type) {
        String at = type.getPackageName();
        if (at.endsWith(".development") || at.endsWith(".seed")
                || type.getSimpleName().matches("Dev(elopment)?[A-Z].*")) {
            return true;
        }
        for (Method method : type.getDeclaredMethods()) {
            RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
            if (mapping != null && Arrays.stream(mapping.path()).anyMatch(path -> path.equals("/dev")
                    || path.startsWith("/dev/") || path.startsWith("/api/development"))) {
                return true;
            }
        }
        return false;
    }
}
