package se.teaterihuskvarna.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import jakarta.persistence.Entity;
import jakarta.validation.Constraint;
import jakarta.validation.Valid;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.Repository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import se.teaterihuskvarna.WithoutMessage;

/// Three things a service gets from an annotation, and loses without a word
/// when the annotation is left out.
class ServiceRulesTest {

    private static final String API = "se.teaterihuskvarna.api";
    private static final String WEB = "se.teaterihuskvarna.web";

    private static final JavaClasses PRODUCTION = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("se.teaterihuskvarna");

    /// A service marked `@Transactional(readOnly = true)` runs every method in
    /// a read-only transaction unless the method has its own `@Transactional`.
    /// In one, Hibernate never flushes, so a change to a loaded entity is
    /// dropped while the method returns the changed record. This follows the
    /// calls a method makes inside its own class, so a write in a private
    /// helper counts for the method that reaches it.
    @Test
    void aMethodThatWritesInAReadOnlyServiceHasItsOwnTransaction() {
        Set<String> wrong = new TreeSet<>();
        int writers = 0;
        for (JavaClass type : PRODUCTION) {
            Transactional onType = type.reflect().getAnnotation(Transactional.class);
            if (onType == null || !onType.readOnly()) {
                continue;
            }
            for (JavaMethod method : type.getMethods()) {
                if (method.getModifiers().contains(JavaModifier.PRIVATE) || method.getName().contains("$")) {
                    continue;
                }
                String write = firstWrite(method);
                if (write == null) {
                    continue;
                }
                writers++;
                Transactional own = method.reflect().getAnnotation(Transactional.class);
                if (own == null || own.readOnly()) {
                    wrong.add("%s.%s reaches %s".formatted(type.getSimpleName(), method.getName(), write));
                }
            }
        }

        assertThat(wrong).as("methods that write in a read-only transaction").isEmpty();
        assertThat(writers).as("writers found, so the test cannot pass by finding none").isGreaterThan(30);
    }

    /// Spring checks a `@Valid` parameter of a service only when the class is
    /// `@Validated`. Without it every constraint on the form is skipped.
    @Test
    void aClassWithAValidParameterIsValidated() {
        Set<String> wrong = new TreeSet<>();
        int checked = 0;
        for (JavaClass imported : PRODUCTION) {
            Class<?> type = imported.reflect();
            if (inAnAdapter(type) || !takesAValidParameter(type)) {
                continue;
            }
            checked++;
            if (!type.isAnnotationPresent(Validated.class)) {
                wrong.add(type.getName());
            }
        }

        assertThat(wrong).as("classes with a @Valid parameter and no @Validated").isEmpty();
        assertThat(checked).as("classes checked, so the test cannot pass by finding none").isGreaterThan(5);
    }

    /// A form's constraints are checked only where a service takes it as
    /// `@Valid`. A public method of a bean that takes one without it stores
    /// whatever was sent.
    @Test
    void aBeanTakesAFormWithConstraintsAsValid() {
        Set<String> wrong = new TreeSet<>();
        int checked = 0;
        for (JavaClass imported : PRODUCTION) {
            Class<?> type = imported.reflect();
            if (inAnAdapter(type) || !AnnotatedElementUtils.hasAnnotation(type, Component.class)) {
                continue;
            }
            for (Method method : type.getDeclaredMethods()) {
                if (!Modifier.isPublic(method.getModifiers())) {
                    continue;
                }
                for (Parameter parameter : method.getParameters()) {
                    if (!declaresConstraints(parameter.getType())) {
                        continue;
                    }
                    checked++;
                    if (!parameter.isAnnotationPresent(Valid.class)) {
                        wrong.add("%s.%s takes %s".formatted(type.getSimpleName(), method.getName(),
                                parameter.getType().getSimpleName()));
                    }
                }
            }
        }

        assertThat(wrong).as("public methods that take a form with constraints and no @Valid").isEmpty();
        assertThat(checked).as("parameters checked, so the test cannot pass by finding none").isGreaterThan(10);
    }

    /// A refusal is an unchecked exception, so the compiler asks nobody to
    /// handle a new one, and the API would answer it with 500. Each one has a
    /// handler in `ProblemResponses` or its own `@ResponseStatus`.
    /// [WithoutMessage] is left out: it is what the log gets in place of an
    /// exception, and nothing throws it.
    @Test
    void everyRefusalHasAnAnswerInTheApi() throws ClassNotFoundException {
        Set<Class<?>> handled = new HashSet<>();
        for (Method method : Class.forName(API + ".ProblemResponses").getDeclaredMethods()) {
            if (method.isAnnotationPresent(ExceptionHandler.class)) {
                handled.addAll(Arrays.asList(method.getAnnotation(ExceptionHandler.class).value()));
                handled.addAll(Arrays.asList(method.getParameterTypes()));
            }
        }
        Set<String> unanswered = new TreeSet<>();
        int refusals = 0;
        for (JavaClass imported : PRODUCTION) {
            Class<?> type = imported.reflect();
            if (!RuntimeException.class.isAssignableFrom(type) || inAnAdapter(type) || type == WithoutMessage.class) {
                continue;
            }
            refusals++;
            if (handled.stream().noneMatch(handler -> handler.isAssignableFrom(type))
                    && !type.isAnnotationPresent(ResponseStatus.class)) {
                unanswered.add(type.getName());
            }
        }

        assertThat(unanswered).as("exceptions without a handler in ProblemResponses").isEmpty();
        assertThat(refusals).as("refusals found, so the test cannot pass by finding none").isGreaterThan(20);
    }

    /// The first write a method reaches through calls inside its own class.
    private static @Nullable String firstWrite(JavaMethod from) {
        Set<JavaCodeUnit> seen = new HashSet<>();
        Deque<JavaCodeUnit> next = new ArrayDeque<>();
        next.add(from);
        while (!next.isEmpty()) {
            JavaCodeUnit code = next.remove();
            if (!seen.add(code)) {
                continue;
            }
            for (JavaMethodCall call : code.getMethodCallsFromSelf()) {
                if (writes(call)) {
                    return call.getTargetOwner().getSimpleName() + "." + call.getName();
                }
                if (call.getTargetOwner().equals(from.getOwner())) {
                    call.getTarget().resolveMember().ifPresent(next::add);
                }
            }
        }
        return null;
    }

    private static boolean writes(JavaMethodCall call) {
        JavaClass owner = call.getTargetOwner();
        String name = call.getName();
        if (owner.isAssignableTo(Repository.class)) {
            return name.startsWith("save") || name.startsWith("delete") || name.startsWith("flush")
                    || call.getTarget().resolveMember().map(method -> method.isAnnotatedWith(Modifying.class))
                            .orElse(false);
        }
        if (owner.isAnnotatedWith(Entity.class)) {
            // A method of an entity that returns nothing changes it.
            return call.getTarget().getRawReturnType().getName().equals("void");
        }
        return owner.isAssignableTo(ApplicationEventPublisher.class) && name.equals("publishEvent")
                || owner.getName().equals("se.teaterihuskvarna.outbox.Outbox") && name.equals("add")
                || owner.getName().equals("se.teaterihuskvarna.login.Mailer") && name.equals("send");
    }

    private static boolean inAnAdapter(Class<?> type) {
        return type.getPackageName().equals(API) || type.getPackageName().equals(WEB);
    }

    private static boolean takesAValidParameter(Class<?> type) {
        return Arrays.stream(type.getDeclaredMethods())
                .flatMap(method -> Arrays.stream(method.getParameters()))
                .anyMatch(parameter -> parameter.isAnnotationPresent(Valid.class));
    }

    private static boolean declaresConstraints(Class<?> type) {
        if (!type.getPackageName().startsWith("se.teaterihuskvarna")) {
            return false;
        }
        for (Field field : type.getDeclaredFields()) {
            for (Annotation annotation : field.getDeclaredAnnotations()) {
                if (annotation.annotationType().isAnnotationPresent(Constraint.class)) {
                    return true;
                }
            }
        }
        return false;
    }
}
