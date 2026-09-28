package se.teaterihuskvarna.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import se.teaterihuskvarna.member.InvitationService;

/// Accepting an invitation over HTTP, the same call the `/inbjudan` button makes.
@RestController
public class InvitationsController {

    private final InvitationService invitations;

    InvitationsController(InvitationService invitations) {
        this.invitations = invitations;
    }

    /// @param acceptance the token from the mailed link
    /// @return 204 when the account was created, 404 when the token is unknown,
    ///         expired or used, 409 when another account took the address meanwhile
    @PostMapping("/api/invitations/acceptance")
    public ResponseEntity<?> accept(@RequestBody @Valid Acceptance acceptance) {
        return switch (invitations.accept(acceptance.token())) {
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
    public record Acceptance(@NotBlank String token) {
    }
}
