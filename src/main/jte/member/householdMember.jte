@import se.teaterihuskvarna.member.ContactForm
@import se.teaterihuskvarna.web.Copy
@import se.teaterihuskvarna.web.CsrfField
@import se.teaterihuskvarna.web.FieldErrors
@param Copy copy
@param CsrfField csrf
@param ContactForm form
@param FieldErrors errors
@param String action
@param boolean stale
@template.member.layout(copy = copy, title = copy.text("member.household.person"), content = @`
    <h1>${copy.text("member.household.person")}</h1>
    <p>${copy.text("member.contact.emailNote")}</p>
    @if(stale)
        <p role="alert">${copy.text("form.stale")}</p>
    @endif
    @template.tag.errors(copy = copy, errors = errors)
    <form method="post" action="${action}">
        @template.tag.csrf(csrf = csrf)
        @template.tag.field(name = "fullName", label = copy.text("member.name"), type = "text",
                autocomplete = "off", value = form.fullName(), error = errors.message("fullName"), required = true)
        @template.tag.field(name = "phone", label = copy.text("member.phone"), type = "tel",
                autocomplete = "off", value = form.phone(), error = errors.message("phone"), required = false)
        @template.tag.field(name = "address", label = copy.text("member.address"), type = "text",
                autocomplete = "off", value = form.address(), error = errors.message("address"), required = false)
        @template.tag.field(name = "postalCode", label = copy.text("member.postalCode"), type = "text",
                autocomplete = "off", value = form.postalCode(), error = errors.message("postalCode"), required = false)
        @template.tag.field(name = "city", label = copy.text("member.city"), type = "text",
                autocomplete = "off", value = form.city(), error = errors.message("city"), required = false)
        <button type="submit">${copy.text("member.contact.submit")}</button>
    </form>
    <p><a href="/medlem/hushall">${copy.text("member.household.back")}</a></p>
`)
