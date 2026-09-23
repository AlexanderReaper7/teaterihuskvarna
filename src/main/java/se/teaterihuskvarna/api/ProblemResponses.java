package se.teaterihuskvarna.api;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import se.teaterihuskvarna.administrator.AdministratorAlreadyExists;
import se.teaterihuskvarna.administrator.CannotRemoveSelf;
import se.teaterihuskvarna.administrator.NoSuchAdministrator;
import se.teaterihuskvarna.administrator.TooFewAdministrators;
import se.teaterihuskvarna.login.NoSuchDevice;
import se.teaterihuskvarna.login.NoSuchPasskey;

/// Turns what a service throws into an HTTP status and an RFC 9457 problem body.
///
/// Limited to this package. The pages handle the same exceptions themselves,
/// because a page answers a failed form with the form again, not with a status.
@RestControllerAdvice(basePackageClasses = ProblemResponses.class)
public class ProblemResponses {

    /// The `errors` property maps each field to its message, which the validator
    /// already resolved to Swedish. The field is the last node of the violation's
    /// path, so `apply.form.fullName` becomes `fullName`, the name the JSON used.
    ///
    /// @param exception what the service's method validation threw
    /// @return 400, with the message for each invalid field
    @ExceptionHandler
    public ProblemDetail invalid(ConstraintViolationException exception) {
        Map<String, String> errors = new TreeMap<>();
        for (ConstraintViolation<?> violation : exception.getConstraintViolations()) {
            errors.merge(field(violation.getPropertyPath()), violation.getMessage(),
                    (first, second) -> first + " " + second);
        }
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle("Invalid input");
        problem.setProperty("errors", errors);
        return problem;
    }

    /// @param exception the service's refusal to add an address twice
    /// @return 409
    @ExceptionHandler
    public ProblemDetail alreadyExists(AdministratorAlreadyExists exception) {
        return problem(HttpStatus.CONFLICT, "Administrator already exists", exception);
    }

    /// @param exception the service's refusal to let an administrator remove themselves
    /// @return 409
    @ExceptionHandler
    public ProblemDetail self(CannotRemoveSelf exception) {
        return problem(HttpStatus.CONFLICT, "Cannot remove oneself", exception);
    }

    /// @param exception the service's refusal to go below two administrators
    /// @return 409
    @ExceptionHandler
    public ProblemDetail tooFew(TooFewAdministrators exception) {
        return problem(HttpStatus.CONFLICT, "Too few administrators", exception);
    }

    /// @param exception the service finding no administrator with that id
    /// @return 404
    @ExceptionHandler
    public ProblemDetail noSuch(NoSuchAdministrator exception) {
        return problem(HttpStatus.NOT_FOUND, "No such administrator", exception);
    }

    /// @param exception the service finding no passkey of the caller's with that id
    /// @return 404
    @ExceptionHandler
    public ProblemDetail noSuch(NoSuchPasskey exception) {
        return problem(HttpStatus.NOT_FOUND, "No such passkey", exception);
    }

    /// @param exception the service finding no device of the caller's with that id
    /// @return 404
    @ExceptionHandler
    public ProblemDetail noSuch(NoSuchDevice exception) {
        return problem(HttpStatus.NOT_FOUND, "No such device", exception);
    }

    private static ProblemDetail problem(HttpStatus status, String title, RuntimeException exception) {
        ProblemDetail problem = ProblemDetail.forStatus(status);
        problem.setTitle(title);
        problem.setDetail(exception.getMessage());
        return problem;
    }

    private static String field(Path path) {
        String last = "";
        for (Path.Node node : path) {
            String name = node.getName();
            last = name == null ? "" : name;
        }
        return last;
    }
}
