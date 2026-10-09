package se.teaterihuskvarna.member;

/// How to pay a fee that is not paid: where to, how much, and what to write in
/// the payment's message so the treasurer can match it to the member.
///
/// @param bankgiro      the association's bankgiro number
/// @param individualOre the fee for one member, in öre
/// @param householdOre  the fee for a household, in öre
/// @param message       what to write in the message, the account's email address
public record PaymentInstruction(String bankgiro, int individualOre, int householdOre, String message) {
}
