package se.teaterihuskvarna.login;

import java.net.URI;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.webauthn.api.ImmutablePublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialRpEntity;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.management.JdbcPublicKeyCredentialUserEntityRepository;
import org.springframework.security.web.webauthn.management.JdbcUserCredentialRepository;
import org.springframework.security.web.webauthn.management.PublicKeyCredentialUserEntityRepository;
import org.springframework.security.web.webauthn.management.UserCredentialRepository;
import org.springframework.security.web.webauthn.management.WebAuthnRelyingPartyOperations;
import org.springframework.security.web.webauthn.management.Webauthn4JRelyingPartyOperations;

/// The passkey store and the relying party, shared by both kinds of login. The
/// filters that use them are per kind, in [PasskeyLogin].
///
/// The relying party is the site address in `teaterihuskvarna.mail.site-url`,
/// the same one login links point at. A passkey belongs to that host name:
/// if the site moves to another domain, every passkey stops working and people
/// log in by link again. See `docs/decisions/0016-passkeys-beside-links.md`.
@Configuration(proxyBeanMethods = false)
class PasskeyConfiguration {

    private static final Locale SWEDISH = Locale.of("sv", "SE");

    /// @param jdbc Spring's JDBC template
    /// @return who owns passkeys, in `user_entities`
    @Bean
    PublicKeyCredentialUserEntityRepository passkeyOwners(JdbcOperations jdbc) {
        return new JdbcPublicKeyCredentialUserEntityRepository(jdbc);
    }

    /// @param jdbc Spring's JDBC template
    /// @return the passkeys themselves, in `user_credentials`
    @Bean
    UserCredentialRepository passkeyCredentials(JdbcOperations jdbc) {
        return new JdbcUserCredentialRepository(jdbc);
    }

    /// Spring stores the owner under the login's principal name, such as
    /// `member:7`, and would send the browser the same as the name to show. A
    /// person would then see `member:7` in their password manager. So the
    /// options a browser gets carry the address and the name instead, with the
    /// stored owner's id unchanged. The stored row keeps the principal name,
    /// which is what login reads back.
    ///
    /// @param owners   who owns passkeys
    /// @param passkeys the passkeys
    /// @param mail     holds the site address the relying party is
    /// @param messages the Swedish copy, for the site name and the administrator label
    /// @return the relying party both kinds of login use
    @Bean
    WebAuthnRelyingPartyOperations relyingParty(PublicKeyCredentialUserEntityRepository owners,
            UserCredentialRepository passkeys, MailSettings mail, MessageSource messages) {
        URI site = mail.siteUrl();
        PublicKeyCredentialRpEntity party = PublicKeyCredentialRpEntity.builder()
                .id(Objects.requireNonNull(site.getHost(), "teaterihuskvarna.mail.site-url has no host"))
                .name(messages.getMessage("site.name", null, SWEDISH))
                .build();
        Webauthn4JRelyingPartyOperations operations = new Webauthn4JRelyingPartyOperations(
                owners, passkeys, party, Set.of(site.getScheme() + "://" + site.getRawAuthority()));
        operations.setCustomizeCreationOptions(options -> {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.getPrincipal() instanceof SignedIn signedIn) {
                PublicKeyCredentialUserEntity stored = owners.findByUsername(signedIn.getUsername());
                if (stored != null) {
                    options.user(ImmutablePublicKeyCredentialUserEntity.builder()
                            .id(stored.getId())
                            .name(shownName(signedIn, messages))
                            .displayName(signedIn.fullName())
                            .build());
                }
            }
        });
        return operations;
    }

    /// The name a password manager lists the passkey under. An administrator's
    /// says so, because one address may have both a member's and an
    /// administrator's passkey, and the browser offers both on either page.
    private static String shownName(SignedIn signedIn, MessageSource messages) {
        return switch (signedIn.kind()) {
            case MEMBER -> signedIn.email();
            case ADMINISTRATOR -> messages.getMessage(
                    "passkey.name.administrator", new Object[] {signedIn.email()}, SWEDISH);
        };
    }
}
