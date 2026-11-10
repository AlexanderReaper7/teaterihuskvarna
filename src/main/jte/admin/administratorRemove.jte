@import se.teaterihuskvarna.administrator.AdministratorDetails
@import se.teaterihuskvarna.web.Copy
@import se.teaterihuskvarna.web.CsrfField
@import se.teaterihuskvarna.web.Feedback
@param Copy copy
@param CsrfField csrf
@param AdministratorDetails administrator
@param Feedback feedback
@template.admin.layout(copy = copy, title = copy.text("admin.remove.heading"), content = @`
    <h1>${copy.text("admin.remove.heading")}</h1>
    @template.tag.feedback(copy = copy, feedback = feedback)
    <p>${copy.text("admin.remove.question", administrator.fullName(), administrator.email())}</p>
    <p>${copy.text("admin.remove.what")}</p>
    <p>${copy.text("admin.remove.again")}</p>
    <div class="buttons">
        <form method="post" action="/admin/administratorer/${administrator.id().value()}/ta-bort">
            @template.tag.csrf(csrf = csrf)
            <button type="submit">@template.icon.trash2() ${copy.text("admin.remove.submit")}</button>
        </form>
    </div>
    <p><a href="/admin">${copy.text("admin.remove.cancel")}</a></p>
`)
