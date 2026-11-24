@import se.teaterihuskvarna.member.MemberFile
@import se.teaterihuskvarna.web.Copy
@import se.teaterihuskvarna.web.CsrfField
@import se.teaterihuskvarna.web.Feedback
@param Copy copy
@param CsrfField csrf
@param MemberFile file
@param Feedback feedback
@template.admin.layout(copy = copy, title = copy.text("register.delete.heading", file.member().fullName()), content = @`
    <h1>${copy.text("register.delete.heading", file.member().fullName())}</h1>
    @template.tag.feedback(copy = copy, feedback = feedback)
    <p>${copy.text("register.delete.body")}</p>
    <form method="post" action="/admin/medlemmar/${file.member().id().value()}/ta-bort">
        @template.tag.csrf(csrf = csrf)
        <button type="submit">@template.icon.trash2() ${copy.text("register.delete.submit")}</button>
    </form>
    <p><a href="/admin/medlemmar/${file.member().id().value()}">${copy.text("register.delete.cancel")}</a></p>
`)
