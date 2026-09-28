@import java.time.Instant
@import se.teaterihuskvarna.mailing.MailingDetails
@import se.teaterihuskvarna.web.Copy
@import se.teaterihuskvarna.web.CsrfField
@param Copy copy
@param CsrfField csrf
@param MailingDetails mailing
@param String testAddress
@param String error
@param String notice
!{Instant sentAt = mailing.sentAt();}
@template.layout(copy = copy, title = mailing.subject(), content = @`
    <h1>${mailing.subject()}</h1>
    <p><a href="/admin/utskick">${copy.text("adminMailings.back")}</a></p>
    @if(error != null)
        <p role="alert">${error}</p>
    @endif
    @if(notice != null)
        <p role="status">${notice}</p>
    @endif
    <dl>
        <dt>${copy.text("adminMailings.log.audience")}</dt>
        <dd>${mailing.audienceName()}</dd>
        <dt>${copy.text("adminMailings.log.recipients")}</dt>
        <dd>${mailing.recipients()}</dd>
        <dt>${copy.text("adminMailings.createdAt")}</dt>
        <dd>${copy.dateTime(mailing.createdAt())}</dd>
        <dt>${copy.text("adminMailings.campaign")}</dt>
        <dd>${mailing.brevoCampaignId()}</dd>
        <dt>${copy.text("adminMailings.log.status")}</dt>
        <dd>${copy.text("adminMailings.status." + mailing.status())}</dd>
        <dt>${copy.text("adminMailings.log.sentAt")}</dt>
        <dd>@if(sentAt != null)${copy.dateTime(sentAt)}@else${copy.text("adminMailings.notSent")}@endif</dd>
    </dl>

    <h2>${copy.text("adminMailings.stats")}</h2>
    <table>
        <caption>${copy.text("adminMailings.stats.caption", copy.dateTime(mailing.checkedAt()))}</caption>
        <tbody>
            <tr><th scope="row">${copy.text("adminMailings.stats.sent")}</th><td>${mailing.sent()}</td></tr>
            <tr><th scope="row">${copy.text("adminMailings.stats.delivered")}</th><td>${mailing.delivered()}</td></tr>
            <tr><th scope="row">${copy.text("adminMailings.log.opened")}</th><td>${mailing.uniqueViews()}</td></tr>
            <tr><th scope="row">${copy.text("adminMailings.log.unsubscribed")}</th><td>${mailing.unsubscriptions()}</td></tr>
            <tr><th scope="row">${copy.text("adminMailings.stats.hardBounces")}</th><td>${mailing.hardBounces()}</td></tr>
        </tbody>
    </table>

    @if(sentAt == null)
        <h2>${copy.text("adminMailings.test")}</h2>
        <p>${copy.text("adminMailings.test.explain", testAddress)}</p>
        <form method="post" action="/admin/utskick/${mailing.id()}/testa">
            @template.tag.csrf(csrf = csrf)
            <button type="submit">${copy.text("adminMailings.test.submit")}</button>
        </form>
        <h2>${copy.text("adminMailings.send")}</h2>
        <p>${copy.text("adminMailings.send.explain", mailing.brevoCampaignId())}</p>
    @endif
`)
