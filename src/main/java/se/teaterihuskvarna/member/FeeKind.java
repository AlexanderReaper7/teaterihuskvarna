package se.teaterihuskvarna.member;

/// What a fee payment covers. Stored by name in `fee.kind`.
public enum FeeKind {

    /// One member.
    INDIVIDUAL,

    /// Everyone in the payer's household at the time the status is read, not at
    /// the time of payment: `V6__fees_households_invitations.sql`.
    HOUSEHOLD
}
