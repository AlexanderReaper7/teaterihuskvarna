package se.teaterihuskvarna.login;

import java.util.OptionalLong;

/// The two things that log in. They have separate login pages and separate
/// lookups, because one address may belong to both an account and an
/// administrator account: `docs/projektplan.md`.
public enum LoginKind {

    /// A member's account.
    MEMBER("member", "ROLE_MEMBER"),

    /// An administrator account, which is not a membership.
    ADMINISTRATOR("administrator", "ROLE_ADMINISTRATOR");

    private final String code;
    private final String role;

    LoginKind(String code, String role) {
        this.code = code;
        this.role = role;
    }

    /// @return the value stored in `one_time_token.kind`, and the prefix of a principal name
    public String code() {
        return code;
    }

    /// @return the Spring Security authority a login of this kind carries
    public String role() {
        return role;
    }

    /// The name Spring Session indexes a session under, so that removing an
    /// administrator can find and end that administrator's sessions. Built from
    /// the id rather than the address, because the address alone does not say
    /// which kind of login a session holds.
    ///
    /// @param id the account's or the administrator's id
    /// @return the principal name, such as `administrator:3`
    public String principalName(long id) {
        return code + ":" + id;
    }

    /// The reverse of [#principalName], for this kind only. A passkey stores its
    /// owner's principal name, and a member's passkey offered on the
    /// administrator login page must not log anyone in there.
    ///
    /// @param principalName a principal name, such as `administrator:3`
    /// @return the id in it, or empty if it is another kind's name or no principal name at all
    public OptionalLong idIn(String principalName) {
        String prefix = code + ":";
        if (!principalName.startsWith(prefix)) {
            return OptionalLong.empty();
        }
        try {
            return OptionalLong.of(Long.parseLong(principalName.substring(prefix.length())));
        } catch (NumberFormatException e) {
            return OptionalLong.empty();
        }
    }
}
