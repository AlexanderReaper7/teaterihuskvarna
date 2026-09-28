package se.teaterihuskvarna.api;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import se.teaterihuskvarna.Violations;
import se.teaterihuskvarna.administrator.AdministratorAlreadyExists;
import se.teaterihuskvarna.administrator.CannotRemoveSelf;
import se.teaterihuskvarna.administrator.NoSuchAdministrator;
import se.teaterihuskvarna.administrator.TooFewAdministrators;
import se.teaterihuskvarna.document.FileTooLarge;
import se.teaterihuskvarna.document.NoSuchDocument;
import se.teaterihuskvarna.document.NotAPdf;
import se.teaterihuskvarna.login.NoSuchDevice;
import se.teaterihuskvarna.login.NoSuchPasskey;
import se.teaterihuskvarna.member.AccountNeedsEmail;
import se.teaterihuskvarna.member.EmailTaken;
import se.teaterihuskvarna.member.FeeAlreadyMarked;
import se.teaterihuskvarna.member.MemberHasAccount;
import se.teaterihuskvarna.member.NoSuchFee;
import se.teaterihuskvarna.member.NoSuchHousehold;
import se.teaterihuskvarna.member.NoSuchMember;
import se.teaterihuskvarna.offer.NoSuchOffer;
import se.teaterihuskvarna.offer.OfferFull;
import se.teaterihuskvarna.offer.RegistrationClosed;

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
            errors.merge(Violations.field(violation), violation.getMessage(),
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

    /// Also what a member gets for an offer that exists but is not published.
    ///
    /// @param exception the service finding no offer with that id
    /// @return 404
    @ExceptionHandler
    public ProblemDetail noSuch(NoSuchOffer exception) {
        return problem(HttpStatus.NOT_FOUND, "No such offer", exception);
    }

    /// @param exception the service's refusal to register beyond the capacity
    /// @return 409
    @ExceptionHandler
    public ProblemDetail full(OfferFull exception) {
        return problem(HttpStatus.CONFLICT, "Offer full", exception);
    }

    /// @param exception the service's refusal to register or cancel after registration closed
    /// @return 409
    @ExceptionHandler
    public ProblemDetail closed(RegistrationClosed exception) {
        return problem(HttpStatus.CONFLICT, "Registration closed", exception);
    }

    /// @param exception the service finding no document with that id
    /// @return 404
    @ExceptionHandler
    public ProblemDetail noSuch(NoSuchDocument exception) {
        return problem(HttpStatus.NOT_FOUND, "No such document", exception);
    }

    /// @param exception the service's refusal of a file that does not start as a PDF does
    /// @return 400
    @ExceptionHandler
    public ProblemDetail notAPdf(NotAPdf exception) {
        return problem(HttpStatus.BAD_REQUEST, "Not a PDF", exception);
    }

    /// @param exception the service's refusal of a file over 10 MB
    /// @return 413
    @ExceptionHandler
    public ProblemDetail tooLarge(FileTooLarge exception) {
        return problem(HttpStatus.CONTENT_TOO_LARGE, "File too large", exception);
    }

    /// @param exception the service finding no member with that id, or none the caller may invite
    /// @return 404
    @ExceptionHandler
    public ProblemDetail noSuch(NoSuchMember exception) {
        return problem(HttpStatus.NOT_FOUND, "No such member", exception);
    }

    /// @param exception the service finding no household with that id
    /// @return 404
    @ExceptionHandler
    public ProblemDetail noSuch(NoSuchHousehold exception) {
        return problem(HttpStatus.NOT_FOUND, "No such household", exception);
    }

    /// @param exception the service finding no payment of the member's own this year
    /// @return 404
    @ExceptionHandler
    public ProblemDetail noSuch(NoSuchFee exception) {
        return problem(HttpStatus.NOT_FOUND, "No such fee", exception);
    }

    /// @param exception the service's refusal to give two accounts one address
    /// @return 409
    @ExceptionHandler
    public ProblemDetail emailTaken(EmailTaken exception) {
        return problem(HttpStatus.CONFLICT, "Email taken", exception);
    }

    /// @param exception the service's refusal to leave an account without an address
    /// @return 409
    @ExceptionHandler
    public ProblemDetail accountNeedsEmail(AccountNeedsEmail exception) {
        return problem(HttpStatus.CONFLICT, "Account needs an email", exception);
    }

    /// @param exception the service's refusal to mark this year's fee twice
    /// @return 409
    @ExceptionHandler
    public ProblemDetail alreadyMarked(FeeAlreadyMarked exception) {
        return problem(HttpStatus.CONFLICT, "Fee already marked", exception);
    }

    /// @param exception the service's refusal to invite a member who can already log in
    /// @return 409
    @ExceptionHandler
    public ProblemDetail hasAccount(MemberHasAccount exception) {
        return problem(HttpStatus.CONFLICT, "Member has an account", exception);
    }

    private static ProblemDetail problem(HttpStatus status, String title, RuntimeException exception) {
        ProblemDetail problem = ProblemDetail.forStatus(status);
        problem.setTitle(title);
        problem.setDetail(exception.getMessage());
        return problem;
    }
}
