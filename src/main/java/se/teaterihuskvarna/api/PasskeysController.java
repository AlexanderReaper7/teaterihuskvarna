package se.teaterihuskvarna.api;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import se.teaterihuskvarna.login.PasskeyDetails;
import se.teaterihuskvarna.login.PasskeyService;
import se.teaterihuskvarna.login.SignedIn;

/// The passkeys of the logged-in account or administrator account: the same
/// list and remove the `/medlem` and `/admin` pages offer. One path each for members and administrators,
/// which the two filter chains guard by kind. Adding a passkey is a browser
/// ceremony against Spring's registration filter, with no endpoint here.
@RestController
public class PasskeysController {

    private final PasskeyService passkeys;

    PasskeysController(PasskeyService passkeys) {
        this.passkeys = passkeys;
    }

    /// @param signedIn the logged-in member or administrator
    /// @return their passkeys, oldest first
    @GetMapping({"/api/member/passkeys", "/api/admin/passkeys"})
    public List<PasskeyDetails> list(@AuthenticationPrincipal SignedIn signedIn) {
        return passkeys.list(signedIn);
    }

    /// @param signedIn the logged-in member or administrator, whose passkey it must be
    /// @param id       the passkey to remove
    @DeleteMapping({"/api/member/passkeys/{id}", "/api/admin/passkeys/{id}"})
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@AuthenticationPrincipal SignedIn signedIn, @PathVariable String id) {
        passkeys.remove(signedIn, id);
    }
}
