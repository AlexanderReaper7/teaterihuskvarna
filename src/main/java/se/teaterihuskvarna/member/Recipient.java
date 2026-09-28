package se.teaterihuskvarna.member;

/// Someone a mailing goes to: a member with an account.
///
/// @param memberId the member's identifier
/// @param fullName the member's name, for the greeting
/// @param email    the account's address
public record Recipient(long memberId, String fullName, String email) {
}
