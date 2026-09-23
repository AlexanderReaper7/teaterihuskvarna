package se.teaterihuskvarna.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import se.teaterihuskvarna.member.ApplicationForm;
import se.teaterihuskvarna.member.MembershipApplicationService;
import se.teaterihuskvarna.member.Welcome;

/// A visitor's membership application over HTTP: the same two service calls the
/// `/bli-medlem` pages make.
@RestController
public class MembershipApplicationsController {

    private final MembershipApplicationService applications;

    MembershipApplicationsController(MembershipApplicationService applications) {
        this.applications = applications;
    }

    /// Always 202 when the form is valid, whether or not the address already has
    /// an account, so the response does not tell who is a member.
    ///
    /// @param form    the applicant's details
    /// @param request the request, for the client address the rate limit counts
    @PostMapping("/api/membership-applications")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void apply(@RequestBody ApplicationForm form, HttpServletRequest request) {
        applications.apply(form, request.getRemoteAddr());
    }

    /// @param confirmation the token from the mailed link
    /// @return 200 with what the welcome page shows, or 404 when the token is
    ///         unknown, expired or already used
    @PostMapping("/api/membership-applications/confirmation")
    public ResponseEntity<Welcome> confirm(@RequestBody @Valid Confirmation confirmation) {
        return ResponseEntity.of(applications.confirm(confirmation.token()));
    }

    /// The body of a confirmation. Checked for a token here, because a missing one
    /// is a malformed request rather than an expired link, and the service should
    /// not have to tell the two apart.
    ///
    /// @param token the token from the mailed link
    public record Confirmation(@NotBlank String token) {
    }
}
