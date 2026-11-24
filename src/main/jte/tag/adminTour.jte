@import se.teaterihuskvarna.web.Copy
@import se.teaterihuskvarna.web.Walkthrough
@param Copy copy
<%-- The administrators' walkthrough: the card /js/tour.js shows, and its steps in order. Nothing in a <template>
     is drawn, so the page is unchanged until the script copies the card into it. A step that names a path
     together with others on the same page relies on the order: tour.js prefers the step the administrator is on.

     Written only while a walkthrough runs, so that a page holds no button's name it does not show:
     se.teaterihuskvarna.web.Walkthrough. --%>
@if(Walkthrough.running())
<template id="tour" data-progress="${copy.text("tour.progress")}" data-done="${copy.text("tour.done")}">
    <section class="tour" data-tour-card aria-labelledby="tour-title" tabindex="-1">
        <p class="tour-progress" data-tour-progress></p>
        <h2 id="tour-title" data-tour-title></h2>
        <div data-tour-body></div>
        <p data-tour-elsewhere>${copy.text("tour.elsewhere")}</p>
        <div class="buttons">
            <button type="button" data-tour-next><span data-tour-next-text>${copy.text("tour.next")}</span> @template.icon.arrowRight()</button>
            <button type="button" class="button-secondary" data-tour-back>@template.icon.arrowLeft() ${copy.text("tour.back")}</button>
            <button type="button" data-tour-resume>@template.icon.route() ${copy.text("tour.resume")}</button>
            <button type="button" class="button-secondary" data-tour-end>@template.icon.x() ${copy.text("tour.end")}</button>
        </div>
    </section>

    @template.tag.tourStep(title = copy.text("tour.welcome.title"), body = copy.text("tour.welcome.body"),
            path = "^/admin$", href = "/admin")
    @template.tag.tourStep(title = copy.text("tour.roles.title"), body = copy.text("tour.roles.body"),
            path = "^/admin$", href = "/admin", target = "#administratorer")

    @template.tag.tourStep(title = copy.text("tour.search.title"), body = copy.text("tour.search.body"),
            path = "^/admin/medlemmar$", href = "/admin/medlemmar", target = "form[role='search']")
    @template.tag.tourStep(title = copy.text("tour.add.title"), body = copy.text("tour.add.body"),
            path = "^/admin/medlemmar$", href = "/admin/medlemmar", target = "a[href='/admin/medlemmar/ny']")
    @template.tag.tourStep(title = copy.text("tour.addForm.title"), body = copy.text("tour.addForm.body"),
            on = "form[method='post'][action='/admin/medlemmar']", href = "/admin/medlemmar/ny",
            target = "form[method='post'][action='/admin/medlemmar']")
    @template.tag.tourStep(title = copy.text("tour.edit.title"), body = copy.text("tour.edit.body"),
            path = "^/admin/medlemmar/[0-9]+$", target = "form:has(#fullName)")
    @template.tag.tourStep(title = copy.text("tour.delete.title"), body = copy.text("tour.delete.body"),
            path = "^/admin/medlemmar/[0-9]+$", target = "a[href$='/ta-bort']")
    @template.tag.tourStep(title = copy.text("tour.deleteConfirm.title"), body = copy.text("tour.deleteConfirm.body"),
            path = "^/admin/medlemmar/[0-9]+/ta-bort$", target = "form[action$='/ta-bort']")

    @template.tag.tourStep(title = copy.text("tour.unpaid.title"), body = copy.text("tour.unpaid.body"),
            path = "^/admin/medlemmar$", href = "/admin/medlemmar", target = "nav.choices")
    @template.tag.tourStep(title = copy.text("tour.markPaid.title"), body = copy.text("tour.markPaid.body"),
            path = "^/admin/medlemmar$", search = "avgift=obetald", href = "/admin/medlemmar?avgift=obetald",
            target = "table")
    @template.tag.tourStep(title = copy.text("tour.householdFee.title"), body = copy.text("tour.householdFee.body"),
            path = "^/admin/medlemmar$", search = "avgift=obetald",
            on = "form:has(input[name='kind'][value='HOUSEHOLD'])",
            target = "form:has(input[name='kind'][value='HOUSEHOLD'])")
    @template.tag.tourStep(title = copy.text("tour.households.title"), body = copy.text("tour.households.body"),
            path = "^/admin/hushall$", href = "/admin/hushall", target = "main > h2")

    @template.tag.tourStep(title = copy.text("tour.offers.title"), body = copy.text("tour.offers.body"),
            path = "^/admin/erbjudanden$", href = "/admin/erbjudanden", target = "table")
    @template.tag.tourStep(title = copy.text("tour.registrations.title"), body = copy.text("tour.registrations.body"),
            path = "^/admin/erbjudanden/[0-9]+$", target = "#anmalda")
    @template.tag.tourStep(title = copy.text("tour.shifts.title"), body = copy.text("tour.shifts.body"),
            path = "^/admin/volontarpass$", href = "/admin/volontarpass", target = "table")
    @template.tag.tourStep(title = copy.text("tour.volunteers.title"), body = copy.text("tour.volunteers.body"),
            path = "^/admin/volontarpass/[0-9]+$", target = "#volontarer")

    @template.tag.tourStep(title = copy.text("tour.export.title"), body = copy.text("tour.export.body"),
            path = "^/admin/medlemmar$", href = "/admin/medlemmar", target = "a[href='/admin/medlemmar.csv']")

    @template.tag.tourStep(title = copy.text("tour.mailing.title"), body = copy.text("tour.mailing.body"),
            on = "form[action='/admin/utskick/forhandsgranska']", href = "/admin/utskick",
            target = "form[action='/admin/utskick/forhandsgranska']")
    @template.tag.tourStep(title = copy.text("tour.preview.title"), body = copy.text("tour.preview.body"),
            on = "iframe.mail-preview", target = "iframe.mail-preview")
    @template.tag.tourStep(title = copy.text("tour.unsubscribe.title"), body = copy.text("tour.unsubscribe.body"),
            on = "iframe.mail-preview", target = "#avsluta-prenumerationen")
    @template.tag.tourStep(title = copy.text("tour.draft.title"), body = copy.text("tour.draft.body"),
            on = "iframe.mail-preview", target = "main > form")
    @template.tag.tourStep(title = copy.text("tour.test.title"), body = copy.text("tour.test.body"),
            path = "^/admin/utskick/[0-9]+$", target = "form[action$='/testa']")
    @template.tag.tourStep(title = copy.text("tour.send.title"), body = copy.text("tour.send.body"),
            path = "^/admin/utskick/[0-9]+$", target = "main > h2:last-of-type")
    @template.tag.tourStep(title = copy.text("tour.log.title"), body = copy.text("tour.log.body"),
            path = "^/admin/utskick$", href = "/admin/utskick", target = "#tidigare-utskick")

    @template.tag.tourStep(title = copy.text("tour.finished.title"), body = copy.text("tour.finished.body"),
            path = "^/admin$", href = "/admin", target = "[data-tour-start]")
</template>
@endif
<script src="/js/tour.js" defer></script>
