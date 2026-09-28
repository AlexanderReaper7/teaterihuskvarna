package se.teaterihuskvarna.member;

/// What the page after a confirmed membership application shows: who is now a
/// member, and where to pay. Confirming does not pay the fee, so the new member
/// is in the register without a paid fee until an administrator marks one.
///
/// @param fullName the new member's name
/// @param email    the address of the new account
/// @param bankgiro the association's bankgiro number, for the fee
/// @param feeIndividualOre the yearly fee for one member, in öre
/// @param feeHouseholdOre  the yearly fee for a household, in öre
public record Welcome(String fullName, String email, String bankgiro, int feeIndividualOre, int feeHouseholdOre) {
}
