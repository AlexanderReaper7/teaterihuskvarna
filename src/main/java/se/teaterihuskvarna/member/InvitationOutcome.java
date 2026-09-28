package se.teaterihuskvarna.member;

/// What happened when someone pressed the button on an invitation link. The
/// invitation is used up in every case.
public enum InvitationOutcome {

    /// The account exists now, with the invited address. Nobody is logged in.
    ACCEPTED,

    /// The link is unknown, expired or already used, or the member is gone.
    INVALID,

    /// Another account took the address after the invitation was sent.
    EMAIL_TAKEN
}
