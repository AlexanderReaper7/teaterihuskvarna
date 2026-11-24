@param String title
@param String body
@param String path = null
@param String search = null
@param String on = null
@param String href = null
@param String target = null
<%-- One step of the walkthrough, read by /js/tour.js. path is a regular expression the page's path has to match,
     search a piece of its query string and on a selector that has to find something: a page a refused form is
     drawn on again keeps the form but not the path. href is where a link to the step leads, and is left out for
     a page only an action leads to. target is what the step points at. --%>
<div data-tour-step data-title="${title}" data-path="${path}" data-search="${search}" data-on="${on}"
        data-href="${href}" data-target="${target}">
    <p>${body}</p>
</div>
