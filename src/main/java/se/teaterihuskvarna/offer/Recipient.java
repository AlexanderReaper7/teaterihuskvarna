package se.teaterihuskvarna.offer;

/// A registered member a mailing to an offer's registrants can reach: one with
/// an account, since the address is on the account.
///
/// @param memberId the member
/// @param fullName the member's name
/// @param email    the member's account address
public record Recipient(long memberId, String fullName, String email) {
}
