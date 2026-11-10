package se.teaterihuskvarna.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.PostMapping;

/// Every controller that takes a post is on [FormModel]'s list.
///
/// The list is by hand on purpose: reading the CSRF token creates a session,
/// which a page without a form must not do. A controller left off it has no
/// `csrf` in its model, and its page fails on the field when it is drawn.
class FormModelTest {

    @Test
    void everyControllerThatTakesAPostGetsTheCsrfField() {
        Set<String> listed = new TreeSet<>();
        for (Class<?> type : FormModel.class.getAnnotation(ControllerAdvice.class).assignableTypes()) {
            listed.add(type.getSimpleName());
        }
        Set<String> posting = new TreeSet<>();
        for (JavaClass controller : new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("se.teaterihuskvarna.web")) {
            Class<?> type = controller.reflect();
            if (type.isAnnotationPresent(Controller.class) && Arrays.stream(type.getDeclaredMethods())
                    .anyMatch(method -> method.isAnnotationPresent(PostMapping.class))) {
                posting.add(type.getSimpleName());
            }
        }

        assertThat(posting).as("controllers with a @PostMapping, so the test cannot pass by finding none")
                .hasSizeGreaterThan(8);
        assertThat(listed).as("the controllers FormModel gives the CSRF field").containsAll(posting);
    }
}
