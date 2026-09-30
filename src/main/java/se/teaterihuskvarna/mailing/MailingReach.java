package se.teaterihuskvarna.mailing;

/// Who a mailing to an audience reaches, counted in Brevo when asked.
///
/// @param members     contacts on the members' list who have not unsubscribed, the mails Brevo will send
/// @param nonMembers  contacts in the audience that are not on the members' list; above 0 the audience is refused
public record MailingReach(long members, long nonMembers) {
}
