package se.teaterihuskvarna;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Path;

/// What both adapters read from a service's method validation. Here rather
/// than in either, since `web` and `api` do not depend on each other.
public final class Violations {

    private Violations() {
    }

    /// A violation on `apply(form, clientAddress)` has the path
    /// `apply.form.fullName`, so the last node names the form field and the
    /// JSON property alike.
    ///
    /// @param violation one violation from the service's method validation
    /// @return the name of the field it is on, such as `fullName`, or an empty string for none
    public static String field(ConstraintViolation<?> violation) {
        String last = "";
        for (Path.Node node : violation.getPropertyPath()) {
            String name = node.getName();
            last = name == null ? "" : name;
        }
        return last;
    }
}
