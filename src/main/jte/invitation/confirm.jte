@import se.teaterihuskvarna.web.Copy
@import se.teaterihuskvarna.web.CsrfField
@import se.teaterihuskvarna.web.Feedback
@param Copy copy
@param CsrfField csrf
@param Feedback feedback
@param String token
@template.layout(copy = copy, title = copy.text("invitation.confirm.heading"), content = @`
    <h1>${copy.text("invitation.confirm.heading")}</h1>
    <p>${copy.text("invitation.confirm.intro")}</p>
    @template.tag.feedback(copy = copy, feedback = feedback)
    <form method="post" action="/inbjudan">
        @template.tag.csrf(csrf = csrf)
        <input type="hidden" name="token" value="${token}">
        <button type="submit">${copy.text("invitation.confirm.submit")}</button>
    </form>
`)
