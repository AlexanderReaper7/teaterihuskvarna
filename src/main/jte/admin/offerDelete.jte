@import se.teaterihuskvarna.offer.OfferDetails
@import se.teaterihuskvarna.web.Copy
@import se.teaterihuskvarna.web.CsrfField
@param Copy copy
@param CsrfField csrf
@param OfferDetails offer
@template.admin.layout(copy = copy, title = copy.text("adminOffers.delete.heading"), content = @`
    <h1>${copy.text("adminOffers.delete.heading")}</h1>
    <p>${copy.text("adminOffers.delete.question", offer.title())}</p>
    @if(offer.registered() > 0)
        <p>${copy.text("adminOffers.delete.registrations", offer.registered())}</p>
    @endif
    <p>${copy.text("adminOffers.delete.final")}</p>
    <div class="buttons">
        <form method="post" action="/admin/erbjudanden/${offer.id()}/ta-bort">
            @template.tag.csrf(csrf = csrf)
            <button type="submit">${copy.text("adminOffers.delete.submit")}</button>
        </form>
    </div>
    <p><a href="/admin/erbjudanden/${offer.id()}">${copy.text("adminOffers.delete.cancel")}</a></p>
`)
