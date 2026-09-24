package se.teaterihuskvarna.administrator;

import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import se.teaterihuskvarna.login.Email;
import se.teaterihuskvarna.login.LoginDirectory;
import se.teaterihuskvarna.login.LoginKind;
import se.teaterihuskvarna.login.SignedIn;

/// Answers the administrator login page: which active administrator account,
/// if any, an address or an id belongs to. A removed administrator is not
/// found, so cannot get a login link or log in with a passkey.
@Component
@Transactional(readOnly = true)
class AdministratorDirectory implements LoginDirectory {

    private final AdministratorRepository administrators;

    AdministratorDirectory(AdministratorRepository administrators) {
        this.administrators = administrators;
    }

    @Override
    public LoginKind kind() {
        return LoginKind.ADMINISTRATOR;
    }

    @Override
    public Optional<SignedIn> find(Email email) {
        return administrators.findActiveByEmail(email.value()).map(AdministratorDirectory::signedIn);
    }

    @Override
    public Optional<SignedIn> findById(long id) {
        return administrators.findActiveById(id).map(AdministratorDirectory::signedIn);
    }

    private static SignedIn signedIn(Administrator administrator) {
        return new SignedIn(
                LoginKind.ADMINISTRATOR, administrator.getId(), administrator.getEmail(), administrator.getFullName());
    }
}
