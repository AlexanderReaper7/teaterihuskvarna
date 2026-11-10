package se.teaterihuskvarna.login;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/// The id of an administrator: `administrator.id`.
///
/// A type of its own, so the compiler tells it from an account's id and a
/// member's, which are numbers too: `se.teaterihuskvarna.member.MemberId`
/// has the reason. Outside the application it is still a number, read by
/// [#valueOf] and by Jackson and written by [#toString].
///
/// @param value the number
public record AdministratorId(@JsonValue long value) implements LoginId {

    /// @param value the number
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public AdministratorId {
    }

    /// @param text the number as a request carries it
    /// @return the id
    /// @throws NumberFormatException if the text is not a number
    public static AdministratorId valueOf(String text) {
        return new AdministratorId(Long.parseLong(text));
    }

    @Override
    public LoginKind kind() {
        return LoginKind.ADMINISTRATOR;
    }

    /// @return the number alone, as a path and a log line want it
    @Override
    public String toString() {
        return Long.toString(value);
    }
}
