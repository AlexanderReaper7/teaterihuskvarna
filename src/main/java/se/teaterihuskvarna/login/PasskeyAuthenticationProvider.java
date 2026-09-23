package se.teaterihuskvarna.login;

import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import java.util.OptionalLong;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.authentication.WebAuthnAuthenticationRequestToken;
import org.springframework.security.web.webauthn.management.WebAuthnRelyingPartyOperations;

/// Turns a signed passkey challenge into who is logged in. One instance per
/// [LoginKind], in place of Spring's `WebAuthnAuthenticationProvider`.
///
/// Spring's provider puts the passkey's owner record in the session as the
/// principal, where every page and endpoint here expects a [SignedIn]. It also
/// looks the owner up by name through a `UserDetailsService`, and the name is
/// a principal name such as `member:7`, which [DirectoryUserDetails] would
/// read as an address. This provider lets Spring check the signature, then
/// reads the id out of the name for its own kind only and asks the
/// [LoginDirectory]. So a member's passkey fails on the administrator login
/// page, and a removed administrator's passkey fails even if it was somehow
/// left behind.
///
/// The result is a `PreAuthenticatedAuthenticationToken`: the relying party
/// has already authenticated the request by the time this provider builds it.
final class PasskeyAuthenticationProvider implements AuthenticationProvider {

    private final LoginKind kind;
    private final WebAuthnRelyingPartyOperations relyingParty;
    private final LoginDirectory directory;

    /// @param kind         the login this provider answers for
    /// @param relyingParty checks the signature and finds the passkey's owner
    /// @param directory    the lookup for that kind of login
    PasskeyAuthenticationProvider(LoginKind kind, WebAuthnRelyingPartyOperations relyingParty,
            LoginDirectory directory) {
        this.kind = kind;
        this.relyingParty = relyingParty;
        this.directory = directory;
    }

    /// @param authentication the signed challenge, as Spring's filter read it
    /// @return the login, with a [SignedIn] as its principal
    /// @throws BadCredentialsException if the signature does not check out, or the
    ///     passkey belongs to another kind of login, or its owner is gone
    @Override
    public Authentication authenticate(Authentication authentication) {
        WebAuthnAuthenticationRequestToken request = (WebAuthnAuthenticationRequestToken) authentication;
        PublicKeyCredentialUserEntity owner;
        try {
            owner = relyingParty.authenticate(request.getWebAuthnRequest());
        } catch (RuntimeException e) {
            throw new BadCredentialsException("Passkey not accepted", e);
        }
        OptionalLong id = kind.idIn(owner.getName());
        Optional<SignedIn> found = id.isPresent() ? directory.findById(id.getAsLong()) : Optional.empty();
        SignedIn signedIn = found.orElseThrow(
                () -> new BadCredentialsException("No " + kind.code() + " login for this passkey"));
        Collection<GrantedAuthority> authorities = new HashSet<>(signedIn.getAuthorities());
        authorities.add(FactorGrantedAuthority.fromAuthority(FactorGrantedAuthority.WEBAUTHN_AUTHORITY));
        return new PreAuthenticatedAuthenticationToken(signedIn, null, authorities);
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return WebAuthnAuthenticationRequestToken.class.isAssignableFrom(authentication);
    }
}
