package se.teaterihuskvarna.login;

import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Component;

/// Ends someone's sessions from outside them, such as when an administrator is
/// removed. Without this a removed administrator keeps a working session for up
/// to 8 hours, with the whole register readable.
///
/// Spring Session indexes each session under the login's principal name,
/// [LoginKind#principalName], which is what [SignedIn#getUsername] returns.
@Component
public class Sessions {

    private final FindByIndexNameSessionRepository<? extends Session> repository;

    Sessions(FindByIndexNameSessionRepository<? extends Session> repository) {
        this.repository = repository;
    }

    /// Deletes every session logged in as this login, in every browser.
    ///
    /// @param id the account's or the administrator's id, whose type says which
    public void end(LoginId id) {
        for (String sessionId : repository.findByPrincipalName(id.principalName()).keySet()) {
            repository.deleteById(sessionId);
        }
    }
}
