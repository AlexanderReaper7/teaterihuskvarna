package se.teaterihuskvarna.member;

/// Who a mailing goes to, for [MemberService#recipients]. Only members with an
/// account can be reached, since the address is on the account.
///
/// The user decided on 2026-09-28 that mailings also go to volunteers and to
/// members registered to an offer. Those need tables this package does not
/// own yet, and each will be a filter over the same members with an account,
/// so a new constant here and a branch in `recipients` is the whole change.
public enum Audience {

    /// Every member with an account.
    ALL,

    /// Members whose fee for this year is paid, by themselves or their household.
    PAID,

    /// Members whose fee for this year is not paid.
    UNPAID
}
