package se.teaterihuskvarna.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;
import org.springframework.validation.BindingResult;
import se.teaterihuskvarna.Swedish;
import se.teaterihuskvarna.document.DocumentForm;
import se.teaterihuskvarna.offer.OfferForm;
import se.teaterihuskvarna.volunteer.ShiftForm;

/// Every form field whose value can be unreadable has its Swedish message.
///
/// A handler method that takes a `BindingResult` after its form gives both to
/// `FieldErrors.of`, which reads the key `<prefix>.<field>.invalid` for each
/// field Spring could not read. A key that is missing is an exception while
/// the request is handled, and only for the one who types such a value, so
/// this fails the build instead.
class UnreadableValueKeysTest {

    /// What each form's keys start with. The controller that reads the form
    /// passes the same word to `FieldErrors.of`.
    private static final Map<Class<?>, String> PREFIXES = Map.of(
            OfferForm.class, "offer",
            ShiftForm.class, "shift",
            DocumentForm.class, "document");

    private final MessageSource messages = new MessagesConfiguration().messageSource();

    /// A form that a new handler reads with a `BindingResult` has to be added to
    /// the map above, which is what makes the test below look at its fields.
    @Test
    void everyFormReadWithABindingResultIsListedHere() {
        assertThat(formsReadWithABindingResult()).containsExactlyInAnyOrderElementsOf(PREFIXES.keySet());
    }

    /// Text always becomes a `String`, so only the other fields need a message.
    @Test
    void everyFieldThatIsNotTextHasAMessage() {
        Set<String> missing = new TreeSet<>();
        PREFIXES.forEach((form, prefix) -> {
            for (RecordComponent field : form.getRecordComponents()) {
                String key = prefix + "." + field.getName() + ".invalid";
                if (field.getType() != String.class && messages.getMessage(key, null, null, Swedish.LOCALE) == null) {
                    missing.add(key);
                }
            }
        });

        assertThat(missing).as("keys missing from messages_sv.properties").isEmpty();
    }

    private static Set<Class<?>> formsReadWithABindingResult() {
        Set<Class<?>> forms = new HashSet<>();
        for (JavaClass controller : new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("se.teaterihuskvarna.web")) {
            for (Method method : controller.reflect().getDeclaredMethods()) {
                Class<?>[] parameters = method.getParameterTypes();
                for (int i = 1; i < parameters.length; i++) {
                    if (BindingResult.class.isAssignableFrom(parameters[i])) {
                        forms.add(parameters[i - 1]);
                    }
                }
            }
        }
        return forms;
    }
}
