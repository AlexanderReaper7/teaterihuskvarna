@import se.teaterihuskvarna.mailing.MailingForm
@import se.teaterihuskvarna.mailing.MailingReach
@import se.teaterihuskvarna.web.Copy
@import se.teaterihuskvarna.web.CsrfField
@param Copy copy
@param CsrfField csrf
@param MailingForm form
@param String html
@param String audienceName
@param MailingReach reach
@template.layout(copy = copy, title = copy.text("adminMailings.preview.heading"), content = @`
    <h1>${copy.text("adminMailings.preview.heading")}</h1>
    <p><a href="/admin/utskick">${copy.text("adminMailings.back")}</a></p>
    <dl>
        <dt>${copy.text("adminMailings.form.audience")}</dt>
        <dd>${audienceName}</dd>
        <dt>${copy.text("adminMailings.preview.reach")}</dt>
        <dd>@if(reach == null)${copy.text("adminMailings.preview.reachUnknown")}@else${copy.text("adminMailings.preview.members", reach.members())}@endif</dd>
        <dt>${copy.text("adminMailings.form.subject")}</dt>
        <dd>${form.subject()}</dd>
    </dl>
    @if(reach != null && reach.nonMembers() > 0)
        <p role="alert">${copy.text("adminMailings.error.nonMembers", reach.nonMembers())}</p>
    @endif
    <p>${copy.text("adminMailings.preview.unsubscribe")}</p>
    <iframe title="${copy.text("adminMailings.preview.frame")}" sandbox srcdoc="${html}"
            style="width:100%;height:70vh;border:1px solid currentColor;background:#fff;"></iframe>
    <p>${copy.text("adminMailings.preview.next")}</p>
    <form method="post">
        @template.tag.csrf(csrf = csrf)
        <input type="hidden" name="audience" value="${form.audience()}">
        <input type="hidden" name="subject" value="${form.subject()}">
        <input type="hidden" name="intro" value="${form.intro()}">
        @for(String slug : form.eventSlugs())
            <input type="hidden" name="events" value="${slug}">
        @endfor
        @for(String slug : form.newsSlugs())
            <input type="hidden" name="news" value="${slug}">
        @endfor
        @if(reach == null || reach.nonMembers() == 0)
            <button type="submit" formaction="/admin/utskick">${copy.text("adminMailings.preview.prepare")}</button>
        @endif
        <button type="submit" formaction="/admin/utskick/andra" class="button-secondary">${copy.text("adminMailings.preview.change")}</button>
    </form>
`)
