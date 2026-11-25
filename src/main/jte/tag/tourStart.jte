@import se.teaterihuskvarna.web.Copy
@param Copy copy
@param String root
@param String explain
<%-- Hidden until /js/tour.js shows it, since the button does nothing without the script. root is the first
     step's page, which the script loads when this page was sent without the steps. --%>
<div data-tour-start="${root}" hidden>
    <p>${explain}</p>
    <button type="button" class="button-secondary">@template.icon.route() ${copy.text("tour.start")}</button>
</div>
