package se.teaterihuskvarna.login;

import java.util.Optional;

/// Finds who an address belongs to, among one kind of login only. The member
/// package answers for accounts and the administrator package for
/// administrators, so this package never touches either one's tables.
public interface LoginDirectory {

    /// @return the kind of login this directory answers for
    LoginKind kind();

    /// @param email an address, already normalised by [Addresses#normalise]
    /// @return who is logged in by that address, or empty if nobody of this kind has it
    Optional<SignedIn> find(String email);

    /// A passkey names its owner by principal name, such as `member:7`, and not
    /// by address, so passkey login looks up by id.
    ///
    /// @param id the account's or the administrator's id
    /// @return who is logged in by that id, or empty if nobody of this kind has it
    Optional<SignedIn> findById(long id);
}
