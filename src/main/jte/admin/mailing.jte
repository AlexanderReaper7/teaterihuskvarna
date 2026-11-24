@import java.time.Instant
@import se.teaterihuskvarna.mailing.MailingDetails
@import se.teaterihuskvarna.web.Copy
@import se.teaterihuskvarna.web.CsrfField
@import se.teaterihuskvarna.web.Feedback
@param Copy copy
@param CsrfField csrf
@param MailingDetails mailing
@param String testAddress
@param Feedback feedback
!{Instant sentAt = mailing.sentAt();}
!{Long campaignId = mailing.brevoCampaignId();}
@template.admin.layout(copy = copy, title = mailing.subject(), content = @`
    <h1>${mailing.subject()}</h1>
    <p><a href="/admin/utskick">${copy.text("adminMailings.back")}</a></p>
    @template.tag.feedback(copy = copy, feedback = feedback)
    <dl>
        <dt>${copy.text("adminMailings.log.audience")}</dt>
        <dd>${mailing.audienceName()}</dd>
        <dt>${copy.text("adminMailings.createdAt")}</dt>
        <dd>${copy.dateTime(mailing.createdAt())}</dd>
        @if(campaignId != null)
            <dt>${copy.text("adminMailings.campaign")}</dt>
            <dd>${campaignId} <a href="https://app.brevo.com/campaigns/listing/email">@template.icon.externalLink() ${copy.text("adminMailings.campaigns.open")}</a></dd>
        @endif
        <dt>${copy.text("adminMailings.log.status")}</dt>
        <dd>${copy.text("adminMailings.status." + mailing.status())}</dd>
        <dt>${copy.text("adminMailings.log.sentAt")}</dt>
        <dd>@if(sentAt != null)${copy.dateTime(sentAt)}@else${copy.text("adminMailings.notSent")}@endif</dd>
    </dl>

    <%-- Until Brevo has sent it, every number would be zero, and the next step is the test. --%>
    @if(campaignId == null)
        <p>${copy.text("adminMailings.creating.explain")}</p>
    @elseif(sentAt == null)
        <h2>${copy.text("adminMailings.test")}</h2>
        <p>${copy.text("adminMailings.test.explain", testAddress)}</p>
        <form method="post" action="/admin/utskick/${mailing.id()}/testa">
            @template.tag.csrf(csrf = csrf)
            <button type="submit">@template.icon.send() ${copy.text("adminMailings.test.submit")}</button>
        </form>
        <h2>${copy.text("adminMailings.send")}</h2>
        <p>${copy.text("adminMailings.send.explain", campaignId)}</p>
    @else
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
    @endif
`)
