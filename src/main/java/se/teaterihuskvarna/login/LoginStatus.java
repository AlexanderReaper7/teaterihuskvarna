package se.teaterihuskvarna.login;

import java.time.Instant;

/// When the asking login ends. The page's script counts down from
/// `secondsLeft` rather than from `endsAt`, because the computer's clock may
/// not agree with the server's.
///
/// @param endsAt      when the login ends unless extended
/// @param secondsLeft seconds from now until then, never below zero
public record LoginStatus(Instant endsAt, long secondsLeft) {
}
