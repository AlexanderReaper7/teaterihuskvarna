package se.teaterihuskvarna.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import gg.jte.generated.precompiled.Templates;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.RecordComponent;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.Invocation;
import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import se.teaterihuskvarna.content.Previews;

/// A value that cannot become the type of its form field never reaches a
/// service: a number no `int` holds, a date a date field would not send, a
/// choice that does not exist.
///
/// Spring leaves such a field null in the form. Null may be a valid value, as
/// it is for an offer without a limit on places, and a service would store it
/// over what the person typed. So a page handler takes a `BindingResult` right
/// after a form that has a field which is not text, and returns before it
/// calls a service when the result has errors.
///
/// Both tests read every handler in this package, so a new one is checked
/// without being listed here.
class UnreadableFormsTest {

    private final Copy copy = new Copy(new MessagesConfiguration().messageSource());

    /// What [PageArguments] fills in. Asked the way Spring asks, so a type it
    /// learns to fill in later is not taken for a form here.
    private static final List<HandlerMethodArgumentResolver> FROM_THE_REQUEST = fromTheRequest();

    /// Without the `BindingResult` Spring answers 400 and the form is lost.
    @Test
    void everyFormWithAFieldThatIsNotTextIsReadWithABindingResult() {
        Set<String> without = new TreeSet<>();
        for (Method handler : handlers()) {
            Parameter[] parameters = handler.getParameters();
            for (int i = 0; i < parameters.length; i++) {
                boolean followed = i + 1 < parameters.length
                        && BindingResult.class.isAssignableFrom(parameters[i + 1].getType());
                if (isForm(parameters[i]) && !fieldsThatAreNotText(parameters[i].getType()).isEmpty() && !followed) {
                    without.add(name(handler));
                }
            }
        }

        assertThat(without).as("handlers whose form needs a BindingResult as the next parameter").isEmpty();
    }

    /// Calls each handler that takes a `BindingResult` with every field that is
    /// not text unreadable, on a controller made of mocks. It fails when the
    /// handler hands the form to anything it was constructed with, other than
    /// the templates, which show the form again. It fails too when the
    /// handler asks for a Swedish message that is missing, which [Copy] throws
    /// on: `FieldErrors.of` reads the key `<prefix>.<field>.invalid` for each
    /// field.
    @Test
    void noHandlerGivesAnUnreadableFormToAService() throws Exception {
        List<String> wrong = new ArrayList<>();
        int called = 0;
        for (Method handler : handlers()) {
            if (Arrays.stream(handler.getParameterTypes()).noneMatch(BindingResult.class::isAssignableFrom)) {
                continue;
            }
            called++;
            List<Object> collaborators = new ArrayList<>();
            Object controller = controller(handler.getDeclaringClass(), collaborators);
            Parameter[] parameters = handler.getParameters();
            @Nullable Object[] arguments = new Object[parameters.length];
            List<Object> forms = new ArrayList<>();
            for (int i = 0; i < parameters.length; i++) {
                Class<?> type = parameters[i].getType();
                if (isForm(parameters[i])) {
                    arguments[i] = form(type);
                    forms.add(arguments[i]);
                } else if (BindingResult.class.isAssignableFrom(type)) {
                    arguments[i] = unreadable(forms.getLast());
                } else {
                    arguments[i] = argument(type);
                }
            }

            handler.setAccessible(true);
            try {
                handler.invoke(controller, arguments);
            } catch (InvocationTargetException e) {
                wrong.add("%s threw %s".formatted(name(handler), e.getCause()));
                continue;
            }
            for (Object collaborator : collaborators) {
                for (Invocation call : mockingDetails(collaborator).getInvocations()) {
                    if (Arrays.stream(call.getArguments()).anyMatch(forms::contains)) {
                        wrong.add("%s gave a form it could not read to %s.%s".formatted(name(handler),
                                call.getMethod().getDeclaringClass().getSimpleName(), call.getMethod().getName()));
                    }
                }
            }
        }

        assertThat(wrong).isEmpty();
        assertThat(called).as("handlers called, so the test cannot pass by finding none").isGreaterThan(3);
    }

    private static List<Method> handlers() {
        List<Method> handlers = new ArrayList<>();
        for (JavaClass controller : new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("se.teaterihuskvarna.web")) {
            for (Method method : controller.reflect().getDeclaredMethods()) {
                if (AnnotatedElementUtils.hasAnnotation(method, RequestMapping.class)) {
                    handlers.add(method);
                }
            }
        }
        return handlers;
    }

    /// A record of the application that Spring fills from the request's
    /// parameters, which it does with or without `@ModelAttribute`. Any other
    /// annotation means something else fills it, as `@AuthenticationPrincipal`
    /// does, and so does [PageArguments] for a `Feedback` and a `CsrfField`.
    private static boolean isForm(Parameter parameter) {
        Class<?> type = parameter.getType();
        MethodParameter asSpringSeesIt = MethodParameter.forParameter(parameter);
        return type.isRecord() && type.getPackageName().startsWith("se.teaterihuskvarna")
                && Arrays.stream(parameter.getAnnotations()).allMatch(ModelAttribute.class::isInstance)
                && FROM_THE_REQUEST.stream().noneMatch(resolver -> resolver.supportsParameter(asSpringSeesIt));
    }

    private static List<HandlerMethodArgumentResolver> fromTheRequest() {
        List<HandlerMethodArgumentResolver> resolvers = new ArrayList<>();
        new PageArguments(mock(Previews.class)).addArgumentResolvers(resolvers);
        assertThat(resolvers).as("what PageArguments fills in").isNotEmpty();
        return resolvers;
    }

    /// Text always becomes a `String`, alone or in a list, so only the other
    /// fields can be unreadable.
    private static List<String> fieldsThatAreNotText(Class<?> form) {
        List<String> fields = new ArrayList<>();
        for (RecordComponent field : form.getRecordComponents()) {
            boolean text = field.getType() == String.class
                    || field.getGenericType().getTypeName().equals("java.util.List<java.lang.String>");
            if (!text) {
                fields.add(field.getName());
            }
        }
        return fields;
    }

    /// The form as Spring leaves it: the text read, every other field null.
    private static Object form(Class<?> type) throws ReflectiveOperationException {
        RecordComponent[] fields = type.getRecordComponents();
        Class<?>[] types = new Class<?>[fields.length];
        @Nullable Object[] values = new Object[fields.length];
        for (int i = 0; i < fields.length; i++) {
            types[i] = fields[i].getType();
            values[i] = types[i] == String.class ? "x" : null;
        }
        Constructor<?> canonical = type.getDeclaredConstructor(types);
        canonical.setAccessible(true);
        return canonical.newInstance(values);
    }

    private static BindingResult unreadable(Object form) {
        BindingResult binding = new BeanPropertyBindingResult(form, "form");
        for (String field : fieldsThatAreNotText(form.getClass())) {
            binding.addError(new FieldError("form", field, "x", true, null, null, null));
        }
        return binding;
    }

    private Object controller(Class<?> type, List<Object> collaborators) throws ReflectiveOperationException {
        Constructor<?>[] constructors = type.getDeclaredConstructors();
        assertThat(constructors).as("constructors of %s", type.getSimpleName()).hasSize(1);
        Class<?>[] types = constructors[0].getParameterTypes();
        Object[] arguments = new Object[types.length];
        for (int i = 0; i < types.length; i++) {
            if (types[i] == Copy.class) {
                arguments[i] = copy;
            } else if (types[i] == Clock.class) {
                arguments[i] = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC);
            } else if (types[i] == Templates.class) {
                arguments[i] = mock(Templates.class);
            } else {
                arguments[i] = mock(types[i], RETURNS_DEEP_STUBS);
                collaborators.add(arguments[i]);
            }
        }
        constructors[0].setAccessible(true);
        return constructors[0].newInstance(arguments);
    }

    private static Object argument(Class<?> type) {
        if (type == long.class || type == Long.class) {
            return 1L;
        }
        if (type == int.class || type == Integer.class) {
            return 1;
        }
        if (type == boolean.class || type == Boolean.class) {
            return false;
        }
        if (type == String.class) {
            return "x";
        }
        if (type == Feedback.class) {
            return new Feedback(false, null, null);
        }
        if (type == CsrfField.class) {
            return new CsrfField("_csrf", "x");
        }
        return mock(type, RETURNS_DEEP_STUBS);
    }

    private static String name(Method handler) {
        return handler.getDeclaringClass().getSimpleName() + "." + handler.getName();
    }
}
