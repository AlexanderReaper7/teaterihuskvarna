package se.teaterihuskvarna.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import se.teaterihuskvarna.member.InvitationOutcome;
import se.teaterihuskvarna.member.InvitationService;

/// An invitation link over HTTP: the same two calls the `/inbjudan` page and
/// its button make.
@RestController
public class InvitationsController {

    private final InvitationService invitations;

    InvitationsController(InvitationService invitations) {
        this.invitations = invitations;
    }

    /// Looks, and uses nothing up: the token still works afterwards.
    ///
    /// @param token the token from the mailed link
    /// @return 204 when an acceptance would create the account now, and otherwise
    ///         what [#accept] would answer: 404 or 409
    @GetMapping("/api/invitations/acceptance")
    public ResponseEntity<?> acceptable(@RequestParam(required = false) @Nullable String token) {
        Optional<InvitationOutcome> refusal = invitations.refusal(token);
        return refusal.isPresent() ? answer(refusal.get()) : ResponseEntity.noContent().build();
    }

    /// @param acceptance the token from the mailed link
    /// @return 204 when the account was created, 404 when the token is unknown,
    ///         expired or used, 409 when another account took the address meanwhile
    @PostMapping("/api/invitations/acceptance")
    public ResponseEntity<?> accept(@RequestBody @Valid Acceptance acceptance) {
        return answer(invitations.accept(acceptance.token()));
    }

    private static ResponseEntity<?> answer(InvitationOutcome outcome) {
        return switch (outcome) {
            case ACCEPTED -> ResponseEntity.noContent().build();
            case INVALID -> ResponseEntity.of(problem(HttpStatus.NOT_FOUND, "No such invitation")).build();
            case EMAIL_TAKEN -> ResponseEntity.of(problem(HttpStatus.CONFLICT, "Email taken")).build();
        };
    }

    private static ProblemDetail problem(HttpStatus status, String title) {
        ProblemDetail problem = ProblemDetail.forStatus(status);
        problem.setTitle(title);
        return problem;
    }

    /// The body of an acceptance.
    ///
    /// @param token the token from the mailed link
    public record Acceptance(@NotBlank(message = "{invitation.invalid.body}") String token) {
    }
}
