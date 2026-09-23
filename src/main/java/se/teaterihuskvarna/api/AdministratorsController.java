package se.teaterihuskvarna.api;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import se.teaterihuskvarna.administrator.AdministratorDetails;
import se.teaterihuskvarna.administrator.AdministratorService;
import se.teaterihuskvarna.administrator.NewAdministrator;
import se.teaterihuskvarna.login.SignedIn;

/// Administrator accounts over HTTP: the same list, add and remove the `/admin`
/// page offers. Errors become statuses in [ProblemResponses].
@RestController
public class AdministratorsController {

    private final AdministratorService administrators;

    AdministratorsController(AdministratorService administrators) {
        this.administrators = administrators;
    }

    /// @return every administrator
    @GetMapping("/api/admin/administrators")
    public List<AdministratorDetails> list() {
        return administrators.list();
    }

    /// @param signedIn the logged-in administrator, recorded as the one who added
    /// @param form     the new administrator's address and name
    /// @return the administrator as stored
    @PostMapping("/api/admin/administrators")
    @ResponseStatus(HttpStatus.CREATED)
    public AdministratorDetails add(@AuthenticationPrincipal SignedIn signedIn, @RequestBody NewAdministrator form) {
        return administrators.add(form, signedIn.id());
    }

    /// @param signedIn the logged-in administrator, recorded as the one who removed
    /// @param id       the administrator to remove, never the caller
    @DeleteMapping("/api/admin/administrators/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@AuthenticationPrincipal SignedIn signedIn, @PathVariable long id) {
        administrators.remove(id, signedIn.id());
    }
}
