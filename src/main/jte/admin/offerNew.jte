@import se.teaterihuskvarna.offer.OfferForm
@import se.teaterihuskvarna.web.Copy
@import se.teaterihuskvarna.web.CsrfField
@import se.teaterihuskvarna.web.FieldErrors
@param Copy copy
@param CsrfField csrf
@param OfferForm form
@param FieldErrors errors
@template.admin.layout(copy = copy, title = copy.text("adminOffers.new.heading"), content = @`
    <h1>${copy.text("adminOffers.new.heading")}</h1>
    <p><a href="/admin/erbjudanden">${copy.text("adminOffers.back")}</a></p>
    <p>${copy.text("adminOffers.new.draft")}</p>
    @template.tag.offerForm(copy = copy, csrf = csrf, form = form, errors = errors, action = "/admin/erbjudanden",
            submit = copy.text("adminOffers.new.submit"))
`)
