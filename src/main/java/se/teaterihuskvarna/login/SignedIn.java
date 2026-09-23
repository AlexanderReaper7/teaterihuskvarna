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
/// @param kind      whether this is a member's account or an administrator account
/// @param id        the account's or the administrator's id
/// @param email     the address the login link went to
/// @param fullName  the name to greet them by
public record SignedIn(LoginKind kind, long id, String email, String fullName) implements UserDetails {

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
