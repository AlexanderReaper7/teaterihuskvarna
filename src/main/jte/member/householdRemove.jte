@import se.teaterihuskvarna.web.Copy
@import se.teaterihuskvarna.web.CsrfField
@param Copy copy
@param CsrfField csrf
@param String fullName
@param long memberId
@param boolean stale
@template.member.layout(copy = copy, title = copy.text("member.household.removeHeading", fullName), content = @`
    <h1>${copy.text("member.household.removeHeading", fullName)}</h1>
    @if(stale)
        <p role="alert">${copy.text("form.stale")}</p>
    @endif
    <p>${copy.text("member.household.removeNote")}</p>
    <p>${copy.text("member.household.removeFeeNote")}</p>
    <form method="post" action="/medlem/hushall/medlemmar/${memberId}/ta-bort">
        @template.tag.csrf(csrf = csrf)
        <button type="submit">${copy.text("member.household.removeSubmit")}</button>
    </form>
    <p><a href="/medlem/hushall">${copy.text("member.household.back")}</a></p>
`)
