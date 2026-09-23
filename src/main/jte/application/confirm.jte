@import se.teaterihuskvarna.web.Copy
@import se.teaterihuskvarna.web.CsrfField
@param Copy copy
@param CsrfField csrf
@param boolean stale
@param String token
@template.layout(copy = copy, title = copy.text("application.confirm.heading"), content = @`
    <h1>${copy.text("application.confirm.heading")}</h1>
    <p>${copy.text("application.confirm.intro")}</p>
    @if(stale)
        <p role="alert">${copy.text("form.stale")}</p>
    @endif
    <form method="post" action="/bli-medlem/bekrafta">
        @template.tag.csrf(csrf = csrf)
        <input type="hidden" name="token" value="${token}">
        <button type="submit">${copy.text("application.confirm.submit")}</button>
    </form>
`)
