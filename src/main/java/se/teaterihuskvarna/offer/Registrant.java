package se.teaterihuskvarna.offer;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/// One registration as an administrator sees it (R020).
///
/// @param memberId     the registered member
/// @param fullName     the member's name
/// @param email        the member's account address, or null when the member has no account
/// @param phone        the member's phone number, or null
/// @param registeredAt when the member registered
public record Registrant(long memberId, String fullName, @Nullable String email, @Nullable String phone,
        Instant registeredAt) {
}
