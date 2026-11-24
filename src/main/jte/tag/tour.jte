@import gg.jte.Content
@import se.teaterihuskvarna.web.Copy
@import se.teaterihuskvarna.web.Walkthrough
@param Copy copy
@param String root
@param Content steps
<%-- A walkthrough: the card /js/tour.js shows, and the steps a layout gives it, tag/adminTour.jte for the
     administrator pages and tag/memberTour.jte for the members'. Nothing in a <template> is drawn, so the page is
     unchanged until the script copies the card into it. root is the section's first page, /admin or /medlem: the
     script keeps one walkthrough per section under it, and its cookie is sent to the pages under it.

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

    ${steps}
</template>
@endif
<script src="/js/tour.js" data-root="${root}" defer></script>
