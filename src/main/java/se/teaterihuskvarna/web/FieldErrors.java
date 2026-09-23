package se.teaterihuskvarna.web;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.jspecify.annotations.Nullable;

/// What was wrong with a submitted form, field by field, for the page that shows
/// the form again.
///
/// Built from the `ConstraintViolationException` the service's method validation
/// throws. The messages are already Swedish: the validator resolved their keys in
/// `messages_sv.properties`.
public final class FieldErrors {

    private static final FieldErrors NONE = new FieldErrors(List.of());

    private final List<InvalidField> fields;

    private FieldErrors(List<InvalidField> fields) {
        this.fields = fields;
    }

    /// @return no errors, for a form shown for the first time
    public static FieldErrors none() {
        return NONE;
    }

    /// A violation on `apply(form, clientAddress)` has the path `apply.form.fullName`,
    /// so the last node names the form field. Sorted by field so the summary does not
    /// change order between two submissions of the same form, which it would if it
    /// followed the validator's unordered set.
    ///
    /// @param exception what the service's method validation threw
    /// @return the errors, one or more per field
    public static FieldErrors of(ConstraintViolationException exception) {
        List<InvalidField> fields = new ArrayList<>();
        for (ConstraintViolation<?> violation : exception.getConstraintViolations()) {
            fields.add(new InvalidField(field(violation.getPropertyPath()), violation.getMessage()));
        }
        fields.sort(Comparator.comparing(InvalidField::field).thenComparing(InvalidField::message));
        return new FieldErrors(List.copyOf(fields));
    }

    /// @return true when the form had nothing wrong with it
    public boolean isEmpty() {
        return fields.isEmpty();
    }

    /// @return every error, for the summary at the top of the form
    public List<InvalidField> all() {
        return List.copyOf(fields);
    }

    /// @param field a form field's name, such as `fullName`
    /// @return the first error on that field, or null when it has none
    public @Nullable String message(String field) {
        for (InvalidField invalid : fields) {
            if (invalid.field().equals(field)) {
                return invalid.message();
            }
        }
        return null;
    }

    private static String field(Path path) {
        String last = "";
        for (Path.Node node : path) {
            String name = node.getName();
            last = name == null ? "" : name;
        }
        return last;
    }

    /// One error on one field.
    ///
    /// @param field   the form field's name, which is also its `id` on the page
    /// @param message the error in Swedish
    public record InvalidField(String field, String message) {
    }
}
