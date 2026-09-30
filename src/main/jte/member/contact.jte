@import se.teaterihuskvarna.member.ContactForm
@import se.teaterihuskvarna.web.Copy
@import se.teaterihuskvarna.web.CsrfField
@import se.teaterihuskvarna.web.FieldErrors
@param Copy copy
@param CsrfField csrf
@param ContactForm form
@param FieldErrors errors
@param boolean stale
@template.member.layout(copy = copy, title = copy.text("member.contact.heading"), content = @`
    <h1>${copy.text("member.contact.heading")}</h1>
    <p>${copy.text("member.contact.emailNote")}</p>
    @if(stale)
        <p role="alert">${copy.text("form.stale")}</p>
    @endif
    @template.tag.errors(copy = copy, errors = errors)
    <form method="post" action="/medlem/kontaktuppgifter">
        @template.tag.csrf(csrf = csrf)
        @template.tag.field(name = "fullName", label = copy.text("member.name"), type = "text",
                autocomplete = "name", value = form.fullName(), error = errors.message("fullName"), required = true)
        @template.tag.field(name = "phone", label = copy.text("member.phone"), type = "tel",
                autocomplete = "tel", value = form.phone(), error = errors.message("phone"), required = false)
        @template.tag.field(name = "address", label = copy.text("member.address"), type = "text",
                autocomplete = "street-address", value = form.address(), error = errors.message("address"),
                required = false)
        @template.tag.field(name = "postalCode", label = copy.text("member.postalCode"), type = "text",
                autocomplete = "postal-code", value = form.postalCode(), error = errors.message("postalCode"),
                required = false)
        @template.tag.field(name = "city", label = copy.text("member.city"), type = "text",
                autocomplete = "address-level2", value = form.city(), error = errors.message("city"),
                required = false)
        <button type="submit">${copy.text("member.contact.submit")}</button>
    </form>
    <p><a href="/medlem">${copy.text("member.back")}</a></p>
`)
