package se.teaterihuskvarna.administrator;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import se.teaterihuskvarna.login.Addresses;
import se.teaterihuskvarna.login.LoginKind;
import se.teaterihuskvarna.login.PasskeyService;
import se.teaterihuskvarna.login.Sessions;

/// Administrators adding and removing each other. The first administrator comes
/// from configuration ([FirstAdministrator]); every one after that is added
/// here. Removal is refused while two or fewer remain: `docs/projektplan.md`.
/// An administrator removes others, never themselves: `GLOSSARY.md`.
@Service
@Validated
@Transactional(readOnly = true)
public class AdministratorService {

    private static final int MINIMUM = 2;

    private final AdministratorRepository administrators;
    private final Sessions sessions;
    private final PasskeyService passkeys;

    AdministratorService(AdministratorRepository administrators, Sessions sessions, PasskeyService passkeys) {
        this.administrators = administrators;
        this.sessions = sessions;
        this.passkeys = passkeys;
    }

    /// @return the administrators who can log in, by name
    public List<AdministratorDetails> list() {
        return administrators.findActive().stream().map(AdministratorDetails::of).toList();
    }

    /// Adds an administrator, or makes a removed one active again if the address
    /// was used before. The row is reused because `administrator_email_key`
    /// spans removed rows too, so a second row for the address cannot exist.
    ///
    /// @param form    the new administrator's address and name
    /// @param addedBy the id of the administrator doing the adding
    /// @return the added administrator
    /// @throws AdministratorAlreadyExists if an active administrator has the address
    /// @throws jakarta.validation.ConstraintViolationException if the form breaks a constraint
    @Transactional
    public AdministratorDetails add(@Valid NewAdministrator form, long addedBy) {
        String email = Addresses.normalise(form.email());
        String fullName = form.fullName().strip();
        Optional<Administrator> existing = administrators.findByEmail(email);
        if (existing.isEmpty()) {
            return AdministratorDetails.of(administrators.save(new Administrator(email, fullName, addedBy)));
        }
        Administrator administrator = existing.get();
        if (administrator.isActive()) {
            throw new AdministratorAlreadyExists();
        }
        administrator.reactivate(fullName, addedBy);
        return AdministratorDetails.of(administrator);
    }

    /// Removes an administrator, their passkeys, and every session they have, so
    /// a removed administrator is logged out at once rather than when the
    /// session expires. A later [#add] of the same address starts with no
    /// passkeys.
    ///
    /// @param id        the administrator to remove
    /// @param removedBy the id of the administrator doing the removing
    /// @throws CannotRemoveSelf if `id` is `removedBy`
    /// @throws NoSuchAdministrator if no active administrator has that id
    /// @throws TooFewAdministrators if two or fewer administrators remain
    @Transactional
    public void remove(long id, long removedBy) {
        if (id == removedBy) {
            throw new CannotRemoveSelf();
        }
        // Counting without the lock lets two administrators remove each other at
        // the same moment: each sees three, each removes one, one is left. With
        // every active row locked, the second removal waits for the first to
        // commit, and PostgreSQL then rereads the rows, drops the one the first
        // removal took out, and hands this transaction the true count.
        List<Administrator> active = administrators.lockActive();
        Administrator administrator = active.stream()
                .filter(candidate -> candidate.getId() == id)
                .findFirst()
                .orElseThrow(NoSuchAdministrator::new);
        if (active.size() <= MINIMUM) {
            throw new TooFewAdministrators();
        }
        administrator.remove(removedBy);
        passkeys.removeAll(LoginKind.ADMINISTRATOR, id);
        sessions.end(LoginKind.ADMINISTRATOR, id);
    }
}
