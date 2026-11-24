package se.teaterihuskvarna.member;

import org.jspecify.annotations.Nullable;

/// What the page after a confirmed membership application shows: who is now a
/// member, and where to pay. Confirming does not pay the fee, so the new member
/// is in the register without a paid fee until an administrator marks one.
///
/// @param fullName the new member's name
/// @param email    the address of the new account
/// @param bankgiro the association's bankgiro number, for the fee
/// @param feeIndividualOre the yearly fee for one member, in öre
/// @param feeHouseholdOre  the yearly fee for a household, in öre
/// @param fee              the new member's fee status for this year, unpaid (R005)
/// @param loginToken       a member login link's token that works only in the browser that sent the
///                         application, posted to `/logga-in/lank` as a mailed one is; null when the
///                         application was sent before the form set a login cookie
public record Welcome(String fullName, String email, String bankgiro, int feeIndividualOre, int feeHouseholdOre,
        FeeStatus fee, @Nullable String loginToken) {
}
