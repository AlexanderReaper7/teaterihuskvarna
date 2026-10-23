package se.teaterihuskvarna.member;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/// One member in the household overview. Contact details are read separately
/// through [HouseholdService#memberForAccount] when editing a household member.
///
/// @param id                the member's identifier, which an invitation names
/// @param fullName          the member's name
/// @param hasAccount        whether the member can log in
/// @param invitationExpires when an open invitation's link stops working, or null for none
public record HouseholdMember(Long id, String fullName, boolean hasAccount, @Nullable Instant invitationExpires) {
}
