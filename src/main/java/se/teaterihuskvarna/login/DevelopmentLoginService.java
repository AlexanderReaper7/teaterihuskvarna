package se.teaterihuskvarna.login;

import java.util.List;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/// Resolves an account for immediate login from the development index.
/// This service is absent unless the `dev` profile is active.
@Service
@Profile("dev")
public class DevelopmentLoginService {

    private final List<LoginDirectory> directories;

    DevelopmentLoginService(List<LoginDirectory> directories) {
        this.directories = List.copyOf(directories);
    }

    /// Uses the current directory, so deleted members and removed administrators
    /// cannot log in through a stale development index.
    ///
    /// @param kind the account kind selected on the index
    /// @param email the account's email address
    /// @return the current account, or empty when it cannot log in
    public Optional<SignedIn> find(LoginKind kind, String email) {
        return Directories.of(kind, directories).find(new Email(email));
    }
}
