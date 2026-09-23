package se.teaterihuskvarna.login;

import java.util.Comparator;
import java.util.List;
import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.security.web.webauthn.api.CredentialRecord;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.management.PublicKeyCredentialUserEntityRepository;
import org.springframework.security.web.webauthn.management.UserCredentialRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/// Listing and removing someone's own passkeys. Adding one is Spring's
/// registration filter ([PasskeyLogin]), because the browser's ceremony talks
/// to it directly.
///
/// Spring's two tables name a passkey's owner by principal name, such as
/// `administrator:3`, with no foreign key to `account` or `administrator`.
/// Nothing in the database removes passkeys with their owner, so whatever
/// removes an administrator or an account must call [#removeAll].
/// [se.teaterihuskvarna.administrator.AdministratorService] does; nothing
/// removes accounts yet. See `docs/decisions/0016-passkeys-beside-links.md`.
@Service
@Transactional(readOnly = true)
public class PasskeyService {

    private final PublicKeyCredentialUserEntityRepository owners;
    private final UserCredentialRepository passkeys;

    PasskeyService(PublicKeyCredentialUserEntityRepository owners, UserCredentialRepository passkeys) {
        this.owners = owners;
        this.passkeys = passkeys;
    }

    /// @param signedIn who is logged in
    /// @return their passkeys, oldest first
    public List<PasskeyDetails> list(SignedIn signedIn) {
        PublicKeyCredentialUserEntity owner = owners.findByUsername(signedIn.getUsername());
        if (owner == null) {
            return List.of();
        }
        return passkeys.findByUserId(owner.getId()).stream()
                .sorted(Comparator.comparing(CredentialRecord::getCreated))
                .map(PasskeyService::details)
                .toList();
    }

    /// Removes one of the logged-in login's passkeys. The authenticator keeps
    /// its half, so the browser may go on offering it until the person deletes
    /// it there too; the site then refuses it.
    ///
    /// @param signedIn who is logged in
    /// @param id       the passkey's id, as [PasskeyDetails#id] gives it
    /// @throws NoSuchPasskey if the id is malformed, unknown, or someone else's
    @Transactional
    public void remove(SignedIn signedIn, String id) {
        PublicKeyCredentialUserEntity owner = owners.findByUsername(signedIn.getUsername());
        Bytes credentialId = parse(id);
        CredentialRecord passkey = passkeys.findByCredentialId(credentialId);
        if (owner == null || passkey == null || !owner.getId().equals(passkey.getUserEntityUserId())) {
            throw new NoSuchPasskey();
        }
        passkeys.delete(credentialId);
    }

    /// Removes every passkey a login has, and the owner row with them. Called
    /// from inside the transaction that removes the login, so both go or
    /// neither does.
    ///
    /// @param kind which kind of login the id belongs to
    /// @param id   the account's or the administrator's id
    @Transactional
    public void removeAll(LoginKind kind, long id) {
        PublicKeyCredentialUserEntity owner = owners.findByUsername(kind.principalName(id));
        if (owner == null) {
            return;
        }
        for (CredentialRecord passkey : passkeys.findByUserId(owner.getId())) {
            passkeys.delete(passkey.getCredentialId());
        }
        owners.delete(owner.getId());
    }

    private static Bytes parse(String id) {
        try {
            return Bytes.fromBase64(id);
        } catch (IllegalArgumentException e) {
            throw new NoSuchPasskey();
        }
    }

    private static PasskeyDetails details(CredentialRecord passkey) {
        return new PasskeyDetails(passkey.getCredentialId().toBase64UrlString(), passkey.getLabel(),
                passkey.getCreated(), passkey.getLastUsed());
    }
}
