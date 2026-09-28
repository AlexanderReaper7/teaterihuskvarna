package se.teaterihuskvarna.member;

/// The fixed audiences for [MemberService#recipients]. Only members with an
/// account can be reached, since the address is on the account.
///
/// Volunteers and the members registered to an offer are audiences too, decided
/// by the user on 2026-09-28, but they need tables other packages own, so
/// `MailingService` asks those packages for them instead.
public enum Audience {

    /// Every member with an account.
    ALL,

    /// Members whose fee for this year is paid, by themselves or their household.
    PAID,

    /// Members whose fee for this year is not paid.
    UNPAID
}
