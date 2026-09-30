@import se.teaterihuskvarna.member.InvitationOutcome
@import se.teaterihuskvarna.web.Copy
@param Copy copy
@param InvitationOutcome outcome
!{String key = switch (outcome) {
    case ACCEPTED -> "invitation.accepted";
    case INVALID -> "invitation.invalid";
    case EMAIL_TAKEN -> "invitation.emailTaken";
};}
@template.layout(copy = copy, title = copy.text(key + ".heading"), content = @`
    <h1>${copy.text(key + ".heading")}</h1>
    <p>${copy.text(key + ".body")}</p>
    @if(outcome == InvitationOutcome.ACCEPTED)
        <p><a href="/logga-in">${copy.text("invitation.accepted.login")}</a></p>
    @endif
`)
