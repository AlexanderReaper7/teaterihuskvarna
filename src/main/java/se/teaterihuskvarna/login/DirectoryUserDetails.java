package se.teaterihuskvarna.login;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

/// Turns the address a redeemed link went to into who is logged in, for Spring's
/// `OneTimeTokenAuthenticationProvider`. One instance per [LoginKind], not a
/// bean: two `UserDetailsService` beans would leave Spring's one-time-token
/// configurer unable to pick one.
///
/// The lookup runs again at login rather than trusting the moment the link was
/// sent. An administrator removed after the link went out gets no session.
final class DirectoryUserDetails implements UserDetailsService {

    private final LoginDirectory directory;

    /// @param directory the lookup for one kind of login
    DirectoryUserDetails(LoginDirectory directory) {
        this.directory = directory;
    }

    /// @param username the address from the redeemed token
    /// @return the [SignedIn] for that address
    /// @throws UsernameNotFoundException if nobody of this kind has the address any more
    @Override
    public UserDetails loadUserByUsername(String username) {
        return directory.find(new Email(username))
                .orElseThrow(() -> new UsernameNotFoundException("No " + directory.kind().code() + " login"));
    }
}
