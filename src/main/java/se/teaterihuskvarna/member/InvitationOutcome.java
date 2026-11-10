package se.teaterihuskvarna.member;

/// What happened when someone pressed the button on an invitation link. The
/// invitation is used up in every case.
///
/// [InvitationService#refusal] answers with the two refusals for a link that
/// is only opened, and uses nothing up.
public enum InvitationOutcome {

    /// The account exists now, with the invited address. Nobody is logged in.
    ACCEPTED,

    /// The link is unknown, expired or already used, or the member is gone.
    INVALID,

    /// Another account took the address after the invitation was sent.
    EMAIL_TAKEN
}
