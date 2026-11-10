package se.teaterihuskvarna.login;

/// The id of something that logs in. Its type says which kind: an
/// [AccountId] is a member's account and an [AdministratorId] an
/// administrator, so a number is never paired with the wrong [LoginKind].
/// [Sessions] and [PasskeyService] take one of these.
public sealed interface LoginId permits AccountId, AdministratorId {

    /// @return which login the id belongs to
    LoginKind kind();

    /// @return the number: `account.id` or `administrator.id`
    long value();

    /// @return the name Spring Session indexes the login's sessions under, such as `administrator:3`
    default String principalName() {
        return kind().principalName(value());
    }
}
