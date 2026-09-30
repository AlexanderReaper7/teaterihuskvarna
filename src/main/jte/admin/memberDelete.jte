@import se.teaterihuskvarna.member.MemberFile
@import se.teaterihuskvarna.web.Copy
@import se.teaterihuskvarna.web.CsrfField
@param Copy copy
@param CsrfField csrf
@param MemberFile file
@param boolean stale
@template.admin.layout(copy = copy, title = copy.text("register.delete.heading", file.member().fullName()), content = @`
    <h1>${copy.text("register.delete.heading", file.member().fullName())}</h1>
    @if(stale)
        <p role="alert">${copy.text("form.stale")}</p>
    @endif
    <p>${copy.text("register.delete.body")}</p>
    <form method="post" action="/admin/medlemmar/${file.member().id()}/ta-bort">
        @template.tag.csrf(csrf = csrf)
        <button type="submit">${copy.text("register.delete.submit")}</button>
    </form>
    <p><a href="/admin/medlemmar/${file.member().id()}">${copy.text("register.delete.cancel")}</a></p>
`)
