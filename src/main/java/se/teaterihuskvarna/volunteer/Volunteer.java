package se.teaterihuskvarna.volunteer;

import java.time.Instant;
import org.jspecify.annotations.Nullable;
import se.teaterihuskvarna.member.MemberId;

/// One booking as an administrator sees it: R020.
///
/// @param memberId the member who booked
/// @param fullName the member's name
/// @param email    the member's account address, or null when the member has no account
/// @param phone    the member's phone number, or null
/// @param bookedAt when the member booked
public record Volunteer(MemberId memberId, String fullName, @Nullable String email, @Nullable String phone,
        Instant bookedAt) {
}
