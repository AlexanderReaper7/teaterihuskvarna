package se.teaterihuskvarna.member;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/// The id of a member: `member.id`.
///
/// A type of its own, so the compiler tells it from an account's id and an
/// administrator's, which are numbers too. While all three were `long`, a
/// handler that passed the logged-in account's id where a member's belongs
/// compiled, and passed any test whose two rows happened to share a number.
///
/// Outside the application it is still a number. Spring reads it from a path
/// or a form field with [#valueOf], Jackson reads and writes the number
/// alone, and [#toString] is the number, for a path built with `+`.
///
/// `IdRulesTest` fails the build on a parameter, field or record component
/// that is named as a member's id and is a `long`.
///
/// @param value the number
public record MemberId(@JsonValue long value) {

    /// @param value the number
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public MemberId {
    }

    /// @param text the number as a request carries it
    /// @return the id
    /// @throws NumberFormatException if the text is not a number
    public static MemberId valueOf(String text) {
        return new MemberId(Long.parseLong(text));
    }

    /// @return the number alone, as a path and a log line want it
    @Override
    public String toString() {
        return Long.toString(value);
    }
}
