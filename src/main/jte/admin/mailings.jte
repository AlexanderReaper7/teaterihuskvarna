@import java.util.List
@import se.teaterihuskvarna.content.Event
@import se.teaterihuskvarna.content.NewsItem
@import se.teaterihuskvarna.mailing.AudienceChoice
@import se.teaterihuskvarna.mailing.MailingAudiences
@import se.teaterihuskvarna.mailing.MailingContent
@import se.teaterihuskvarna.mailing.MailingDetails
@import se.teaterihuskvarna.mailing.MailingForm
@import se.teaterihuskvarna.web.Copy
@import se.teaterihuskvarna.web.CsrfField
@import se.teaterihuskvarna.web.FieldErrors
@param String studioUrl = null
@param Copy copy
@param CsrfField csrf
@param MailingForm form
@param FieldErrors errors
@param MailingAudiences audiences
@param MailingContent content
@param List<MailingDetails> log
@param String error
@param String notice
!{String audienceError = errors.message("audience");}
!{String introError = errors.message("intro");}
@template.admin.layout(copy = copy, title = copy.text("adminMailings.heading"), content = @`
    <h1>${copy.text("adminMailings.heading")}</h1>
    <p><a href="/admin">${copy.text("admin.toOverview")}</a></p>
    @if(error != null)
        <p role="alert">${error}</p>
    @endif
    @if(notice != null)
        <p role="status">${notice}</p>
    @endif

    <h2>${copy.text("adminMailings.new")}</h2>
    <p>${copy.text("adminMailings.how")}</p>
    @if(studioUrl != null)
        <p><a href="${studioUrl}">${copy.text("adminMailings.content.open")}</a></p>
    @else
        <p>${copy.text("admin.content.unavailable")} <a href="https://www.sanity.io/manage">${copy.text("admin.content.manage")}</a></p>
    @endif
    @template.tag.errors(copy = copy, errors = errors)
    <form method="post" action="/admin/utskick/forhandsgranska">
        @template.tag.csrf(csrf = csrf)
        <p>
            <label for="audience">${copy.text("adminMailings.form.audience")}</label>
            <select id="audience" name="audience" required aria-invalid="${audienceError == null ? null : "true"}"
                    aria-describedby="${audienceError == null ? "audience-hint" : "audience-hint audience-error"}">
                <option value="">${copy.text("adminMailings.form.chooseAudience")}</option>
                @for(AudienceChoice choice : audiences.choices())
                    <option value="${choice.value()}" selected="${choice.value().equals(form.audience())}">${choice.name()}</option>
                @endfor
            </select>
            @if(audienceError != null)
                <span id="audience-error" class="field-error">${audienceError}</span>
            @endif
        </p>
        <p id="audience-hint">${copy.text("adminMailings.segments")} <a href="https://app.brevo.com/contact/segment">${copy.text("adminMailings.segments.open")}</a></p>
        @if(audiences.segmentsUnavailable())
            <p role="alert">${copy.text("adminMailings.segmentsUnavailable")}</p>
        @endif
        @template.tag.field(name = "subject", label = copy.text("adminMailings.form.subject"), type = "text",
                autocomplete = "off", value = form.subject(), error = errors.message("subject"), required = true)
        <p>
            <label for="intro">${copy.text("adminMailings.form.intro")}</label>
            <textarea id="intro" name="intro" rows="8" aria-invalid="${introError == null ? null : "true"}"
                      aria-describedby="${introError == null ? "intro-hint" : "intro-hint intro-error"}">${form.intro()}</textarea>
            <span id="intro-hint">${copy.text("adminMailings.form.introHint")}</span>
            @if(introError != null)
                <span id="intro-error" class="field-error">${introError}</span>
            @endif
        </p>
        <fieldset>
            <legend>${copy.text("adminMailings.form.events")}</legend>
            @if(content.events().isEmpty())
                <p>${copy.text("adminMailings.form.noEvents")}</p>
            @endif
            @for(Event event : content.events())
                <label><input type="checkbox" name="events" value="${event.slug()}"
                              checked="${form.eventSlugs().contains(event.slug())}"> ${event.title()}, ${copy.dateTime(event.startsAt())}</label><br>
            @endfor
        </fieldset>
        <fieldset>
            <legend>${copy.text("adminMailings.form.news")}</legend>
            @if(content.news().isEmpty())
                <p>${copy.text("adminMailings.form.noNews")}</p>
            @endif
            @for(NewsItem item : content.news())
                <label><input type="checkbox" name="news" value="${item.slug()}"
                              checked="${form.newsSlugs().contains(item.slug())}"> ${item.title()}, ${copy.date(item.publishedAt())}</label><br>
            @endfor
        </fieldset>
        <button type="submit">${copy.text("adminMailings.form.preview")}</button>
    </form>

    <h2>${copy.text("adminMailings.log")}</h2>
    @if(log.isEmpty())
        <p>${copy.text("adminMailings.log.none")}</p>
    @else
        <table>
            <caption>${copy.text("adminMailings.log.caption")}</caption>
            <thead>
                <tr>
                    <th scope="col">${copy.text("adminMailings.log.subject")}</th>
                    <th scope="col">${copy.text("adminMailings.log.audience")}</th>
                    <th scope="col">${copy.text("adminMailings.log.status")}</th>
                    <th scope="col">${copy.text("adminMailings.log.sentAt")}</th>
                    <th scope="col">${copy.text("adminMailings.stats.sent")}</th>
                    <th scope="col">${copy.text("adminMailings.log.opened")}</th>
                    <th scope="col">${copy.text("adminMailings.log.unsubscribed")}</th>
                </tr>
            </thead>
            <tbody>
                @for(MailingDetails mailing : log)
                    !{java.time.Instant sentAt = mailing.sentAt();}
                    <tr>
                        <th scope="row"><a href="/admin/utskick/${mailing.id()}">${mailing.subject()}</a></th>
                        <td>${mailing.audienceName()}</td>
                        <td>${copy.text("adminMailings.status." + mailing.status())}</td>
                        <td>@if(sentAt != null)${copy.dateTime(sentAt)}@else${copy.text("adminMailings.notSent")}@endif</td>
                        <td>${mailing.sent()}</td>
                        <td>${mailing.uniqueViews()}</td>
                        <td>${mailing.unsubscriptions()}</td>
                    </tr>
                @endfor
            </tbody>
        </table>
    @endif
`)
