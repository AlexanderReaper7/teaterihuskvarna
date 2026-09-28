@import java.util.List
@import se.teaterihuskvarna.member.HouseholdDetails
@import se.teaterihuskvarna.member.MemberForm
@import se.teaterihuskvarna.web.Copy
@import se.teaterihuskvarna.web.CsrfField
@import se.teaterihuskvarna.web.FieldErrors
@param Copy copy
@param CsrfField csrf
@param MemberForm form
@param FieldErrors errors
@param List<HouseholdDetails> households
@param String error
@param boolean stale
@template.layout(copy = copy, title = copy.text("register.add"), content = @`
    <h1>${copy.text("register.add")}</h1>
    <p><a href="/admin/medlemmar">${copy.text("register.toList")}</a></p>
    @if(stale)
        <p role="alert">${copy.text("form.stale")}</p>
    @endif
    @if(error != null)
        <p role="alert">${error}</p>
    @endif
    <p>${copy.text("register.form.emailNote")}</p>
    @template.tag.errors(copy = copy, errors = errors)
    <form method="post" action="/admin/medlemmar">
        @template.tag.csrf(csrf = csrf)
        @template.tag.memberFields(copy = copy, form = form, errors = errors, households = households)
        <button type="submit">${copy.text("register.add.submit")}</button>
    </form>
`)
