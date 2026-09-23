@import se.teaterihuskvarna.member.ApplicationForm
@import se.teaterihuskvarna.web.Copy
@import se.teaterihuskvarna.web.CsrfField
@import se.teaterihuskvarna.web.FieldErrors
@param Copy copy
@param CsrfField csrf
@param boolean stale
@param ApplicationForm form
@param FieldErrors errors
@template.layout(copy = copy, title = copy.text("application.heading"), content = @`
    <h1>${copy.text("application.heading")}</h1>
    <p>${copy.text("application.pitch")}</p>
    <h2>${copy.text("application.benefits.heading")}</h2>
    <ul>
        <li>${copy.text("application.benefit.support")}</li>
        <li>${copy.text("application.benefit.information")}</li>
        <li>${copy.text("application.benefit.offers")}</li>
    </ul>
    <h2>${copy.text("application.fee.heading")}</h2>
    <p>${copy.text("application.fee.body")}</p>
    <p>${copy.text("application.household")}</p>
    <p>${copy.text("application.intro")}</p>
    @if(stale)
        <p role="alert">${copy.text("form.stale")}</p>
    @endif
    @template.tag.errors(copy = copy, errors = errors)
    <form method="post" action="/bli-medlem">
        @template.tag.csrf(csrf = csrf)
        @template.tag.field(name = "fullName", label = copy.text("application.form.fullName"), type = "text",
                autocomplete = "name", value = form.fullName(), error = errors.message("fullName"), required = true)
        @template.tag.field(name = "email", label = copy.text("application.form.email"), type = "email",
                autocomplete = "email", value = form.email(), error = errors.message("email"), required = true)
        @template.tag.field(name = "phone", label = copy.text("application.form.phone"), type = "tel",
                autocomplete = "tel", value = form.phone(), error = errors.message("phone"), required = false)
        @template.tag.field(name = "address", label = copy.text("application.form.address"), type = "text",
                autocomplete = "street-address", value = form.address(), error = errors.message("address"),
                required = false)
        @template.tag.field(name = "postalCode", label = copy.text("application.form.postalCode"), type = "text",
                autocomplete = "postal-code", value = form.postalCode(), error = errors.message("postalCode"),
                required = false)
        @template.tag.field(name = "city", label = copy.text("application.form.city"), type = "text",
                autocomplete = "address-level2", value = form.city(), error = errors.message("city"),
                required = false)
        <button type="submit">${copy.text("application.submit")}</button>
    </form>
`)
