@import se.teaterihuskvarna.mailing.MailingForm
@import se.teaterihuskvarna.mailing.MailingReach
@import se.teaterihuskvarna.web.Copy
@import se.teaterihuskvarna.web.CsrfField
@import se.teaterihuskvarna.web.Feedback
@param Copy copy
@param CsrfField csrf
@param MailingForm form
@param String mailKey
@param String audienceName
@param MailingReach reach
@param Feedback feedback
@template.admin.layout(copy = copy, title = copy.text("adminMailings.preview.heading"), content = @`
    <h1>${copy.text("adminMailings.preview.heading")}</h1>
    <p><a href="/admin/utskick">${copy.text("adminMailings.back")}</a></p>
    @template.tag.feedback(copy = copy, feedback = feedback)
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
    @elseif(reach != null && reach.members() == 0)
        <p role="alert">${copy.text("adminMailings.error.noRecipients")}</p>
    @endif
    <p>${copy.text("adminMailings.preview.unsubscribe")}</p>
    <iframe class="mail-preview" title="${copy.text("adminMailings.preview.frame")}" sandbox
            src="/admin/utskick/forhandsgranska/${mailKey}"></iframe>
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
        <div class="buttons">
            @if(reach == null || (reach.nonMembers() == 0 && reach.members() > 0))
                <button type="submit" formaction="/admin/utskick">@template.icon.plus() ${copy.text("adminMailings.preview.prepare")}</button>
            @endif
            <button type="submit" formaction="/admin/utskick/andra" class="button-secondary">@template.icon.pencil() ${copy.text("adminMailings.preview.change")}</button>
        </div>
    </form>
`)
