package se.teaterihuskvarna.web;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import se.teaterihuskvarna.Violations;

/// What was wrong with a submitted form, field by field, for the page that shows
/// the form again.
///
/// Built from the `ConstraintViolationException` the service's method validation
/// throws. The messages are already Swedish: the validator resolved their keys in
/// `messages_sv.properties`.
///
/// Or built from a `BindingResult`, for a value that never reached the service
/// because it could not become the type of its field.
public final class FieldErrors {

    private static final FieldErrors NONE = new FieldErrors(List.of(), Map.of());

    private final List<InvalidField> fields;

    /// What was typed in each field that could not be read, by field name.
    private final Map<String, String> unreadable;

    private FieldErrors(List<InvalidField> fields, Map<String, String> unreadable) {
        this.fields = fields;
        this.unreadable = unreadable;
    }

    /// @return no errors, for a form shown for the first time
    public static FieldErrors none() {
        return NONE;
    }

    /// The field is the last node of the violation's path ([Violations#field]).
    /// Sorted by field so the summary does not
    /// change order between two submissions of the same form, which it would if it
    /// followed the validator's unordered set.
    ///
    /// @param exception what the service's method validation threw
    /// @return the errors, one or more per field
    public static FieldErrors of(ConstraintViolationException exception) {
        List<InvalidField> fields = new ArrayList<>();
        for (ConstraintViolation<?> violation : exception.getConstraintViolations()) {
            fields.add(new InvalidField(Violations.field(violation), violation.getMessage()));
        }
        fields.sort(Comparator.comparing(InvalidField::field).thenComparing(InvalidField::message));
        return new FieldErrors(List.copyOf(fields), Map.of());
    }

    /// For a form with a value that could not become the type of its field: a
    /// number no `int` holds, a date a date field would not send, a choice that
    /// does not exist. Spring leaves such a field null in the form and reports it
    /// in the `BindingResult` that the handler method takes right after the form.
    /// Without that parameter Spring answers 400 and the form is lost.
    ///
    /// The message for the field `capacity` of a form with the prefix `offer` is
    /// the key `offer.capacity.invalid`.
    ///
    /// A handler that gets errors here returns before it calls the service: the
    /// form's null may be a valid value, which the service would then store.
    /// `UnreadableFormsTest` calls every handler with every such field unreadable,
    /// and fails when a form reaches a service, when a key is missing, and when
    /// a form with such a field is read without a `BindingResult`.
    ///
    /// @param binding what Spring could not read
    /// @param copy    the Swedish copy
    /// @param prefix  what the form's keys start with, such as `offer`
    /// @return one error for each unreadable field, with what was typed in it
    public static FieldErrors of(BindingResult binding, Copy copy, String prefix) {
        List<InvalidField> fields = new ArrayList<>();
        Map<String, String> unreadable = new HashMap<>();
        for (FieldError error : binding.getFieldErrors()) {
            String field = error.getField();
            fields.add(new InvalidField(field, copy.text(prefix + "." + field + ".invalid")));
            if (error.getRejectedValue() instanceof String typed) {
                unreadable.put(field, typed);
            }
        }
        fields.sort(Comparator.comparing(InvalidField::field));
        return new FieldErrors(List.copyOf(fields), Map.copyOf(unreadable));
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

    /// What the page puts back in a field. A field that could not be read is null
    /// in the form, so what was typed comes from here instead.
    ///
    /// @param field a form field's name, such as `capacity`
    /// @param bound the field's value in the form, or null
    /// @return what was typed if it could not be read, else the form's value as
    ///         text, or null for an empty field
    public @Nullable String value(String field, @Nullable Object bound) {
        String typed = unreadable.get(field);
        if (typed != null) {
            return typed;
        }
        return bound == null ? null : bound.toString();
    }

    /// One error on one field.
    ///
    /// @param field   the form field's name, which is also its `id` on the page
    /// @param message the error in Swedish
    public record InvalidField(String field, String message) {
    }
}
