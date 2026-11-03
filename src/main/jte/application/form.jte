@import se.teaterihuskvarna.member.ApplicationForm
@import se.teaterihuskvarna.member.AssociationSettings
@import se.teaterihuskvarna.web.Copy
@import se.teaterihuskvarna.web.PageMetadata
@import se.teaterihuskvarna.web.CsrfField
@import se.teaterihuskvarna.web.FieldErrors
@param Copy copy
@param PageMetadata metadata
@param CsrfField csrf
@param boolean stale
@param ApplicationForm form
@param FieldErrors errors
@param AssociationSettings fees
@template.layout(metadata = metadata, copy = copy, title = copy.text("application.heading"), content = @`
    <h1>${copy.text("application.heading")}</h1>
    <p>${copy.text("application.fee.body", copy.kronor(fees.feeIndividualOre()),
        copy.kronor(fees.feeHouseholdOre()))}</p>
    <p>${copy.text("application.household")}</p>
    <p>${copy.text("application.intro")}</p>
    @if(stale)
        <p role="alert">${copy.text("form.stale")}</p>
    @endif
    @template.tag.errors(copy = copy, errors = errors)
    <form method="post" action="/bli-medlem">
        @template.tag.csrf(csrf = csrf)
        @template.tag.field(copy = copy, name = "fullName", label = copy.text("member.name"), type = "text",
                autocomplete = "name", value = form.fullName(), error = errors.message("fullName"), required = true)
        @template.tag.field(copy = copy, name = "email", label = copy.text("member.email"), type = "email",
                autocomplete = "email", value = form.email(), error = errors.message("email"), required = true)
        @template.tag.field(copy = copy, name = "phone", label = copy.text("member.phone"), type = "tel",
                autocomplete = "tel", value = form.phone(), error = errors.message("phone"), required = false)
        @template.tag.field(copy = copy, name = "address", label = copy.text("member.address"), type = "text",
                autocomplete = "street-address", value = form.address(), error = errors.message("address"),
                required = false)
        @template.tag.field(copy = copy, name = "postalCode", label = copy.text("member.postalCode"), type = "text",
                autocomplete = "postal-code", value = form.postalCode(), error = errors.message("postalCode"),
                required = false)
        @template.tag.field(copy = copy, name = "city", label = copy.text("member.city"), type = "text",
                autocomplete = "address-level2", value = form.city(), error = errors.message("city"),
                required = false)
        <button type="submit">${copy.text("application.submit")}</button>
    </form>
    <h2>${copy.text("application.benefits.heading")}</h2>
    <ul>
        <li>${copy.text("application.benefit.support")}</li>
        <li>${copy.text("application.benefit.information")}</li>
        <li>${copy.text("application.benefit.offers")}</li>
    </ul>
`)
