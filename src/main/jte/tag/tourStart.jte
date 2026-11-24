@import se.teaterihuskvarna.web.Copy
@param Copy copy
<%-- Hidden until /js/tour.js shows it, since the button does nothing without the script. The attribute is the
     first step's page, which the script loads when this page was sent without the steps. --%>
<div data-tour-start="/admin" hidden>
    <p>${copy.text("tour.start.explain")}</p>
    <button type="button" class="button-secondary">@template.icon.route() ${copy.text("tour.start")}</button>
</div>
