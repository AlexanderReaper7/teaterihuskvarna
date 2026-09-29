package se.teaterihuskvarna.member;

/// Something a member's Brevo contact shows has changed: their name, address,
/// fee, household, offers or shifts, or the member is gone. Published inside
/// the transaction that made the change, so a listener can write to the
/// outbox in it: `docs/decisions/0026-outbox-and-brevo-contacts.md`.
///
/// @param memberId the member, who may no longer exist
public record MemberChanged(long memberId) {
}
