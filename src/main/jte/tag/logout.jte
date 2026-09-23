@import se.teaterihuskvarna.web.Copy
@import se.teaterihuskvarna.web.CsrfField
@param Copy copy
@param CsrfField csrf
@param String action
<form method="post" action="${action}">
    @template.tag.csrf(csrf = csrf)
    <button type="submit" class="button-secondary">${copy.text("logout.submit")}</button>
</form>
