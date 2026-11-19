package se.teaterihuskvarna.login;

import java.util.Collection;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/// Who is logged in. Stored in the session, which Spring Session serialises to
/// PostgreSQL, so everything here must be serialisable and none of it may be an
/// entity.
///
/// The id is a bare number here because the record is what the session
/// stores, and a session written before the id types existed must still be
/// read. Code outside this package never reads [#id]: it asks for
/// [#accountId] or [#administratorId], whose types cannot be passed for one
/// another or for a member's id, and it makes a `SignedIn` with [#of].
/// `IdRulesTest` fails the build otherwise.
///
/// @param kind      whether this is a member's account or an administrator account
/// @param id        the account's or the administrator's id
/// @param email     the address the login link went to
/// @param fullName  the name to greet them by
public record SignedIn(LoginKind kind, long id, String email, String fullName) implements UserDetails {

    /// @param id       whose login it is, which also says what kind
    /// @param email    the address the login link went to
    /// @param fullName the name to greet them by
    /// @return the login
    public static SignedIn of(LoginId id, String email, String fullName) {
        return new SignedIn(id.kind(), id.value(), email, fullName);
    }

    /// @return the id with its kind in its type
    public LoginId loginId() {
        return switch (kind) {
            case MEMBER -> new AccountId(id);
            case ADMINISTRATOR -> new AdministratorId(id);
        };
    }

    /// For code that only a member's login reaches, as the security
    /// configuration sees to for `/medlem` and `/api/member`.
    ///
    /// @return the logged-in account's id
    /// @throws IllegalStateException if an administrator is logged in
    public AccountId accountId() {
        if (kind != LoginKind.MEMBER) {
            throw new IllegalStateException("not a member's login: " + kind);
        }
        return new AccountId(id);
    }

    /// For code that only an administrator's login reaches.
    ///
    /// @return the logged-in administrator's id
    /// @throws IllegalStateException if a member is logged in
    public AdministratorId administratorId() {
        if (kind != LoginKind.ADMINISTRATOR) {
            throw new IllegalStateException("not an administrator's login: " + kind);
        }
        return new AdministratorId(id);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(kind.role()));
    }

    /// There is no password. A login link is the only credential.
    @Override
    public @Nullable String getPassword() {
        return null;
    }

    @Override
    public String getUsername() {
        return kind.principalName(id);
    }
}
