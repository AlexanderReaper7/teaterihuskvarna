package se.teaterihuskvarna.member;

import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;

/// Everything an administrator sees about one member (R018): the details, the
/// fee status for every year with a payment and for this year, the household,
/// and any open invitation.
///
/// @param member            the member's details, with this year's fee status
/// @param fees              one status per year, newest first, this year always included
/// @param household         the household and its members, or null for none
/// @param invitationExpires when the open invitation's link stops working, or null for none
public record MemberFile(
        MemberDetails member,
        List<FeeStatus> fees,
        @Nullable HouseholdDetails household,
        @Nullable Instant invitationExpires) {

    /// @param member            the member's details
    /// @param fees              one status per year, copied
    /// @param household         the household, or null
    /// @param invitationExpires when the open invitation expires, or null
    public MemberFile {
        fees = List.copyOf(fees);
    }
}
