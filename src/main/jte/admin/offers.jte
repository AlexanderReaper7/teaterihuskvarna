@import java.util.List
@import se.teaterihuskvarna.offer.OfferDetails
@import se.teaterihuskvarna.web.Copy
@param Copy copy
@param List<OfferDetails> offers
@param String error
@param String notice
@template.admin.layout(copy = copy, title = copy.text("adminOffers.heading"), content = @`
    <h1>${copy.text("adminOffers.heading")}</h1>
    <p><a href="/admin">${copy.text("admin.toOverview")}</a></p>
    @if(error != null)
        <p role="alert">${error}</p>
    @endif
    @if(notice != null)
        <p role="status">${notice}</p>
    @endif
    <p><a href="/admin/erbjudanden/nytt">${copy.text("adminOffers.new")}</a></p>
    @if(offers.isEmpty())
        <p>${copy.text("adminOffers.none")}</p>
    @else
        @template.tag.offerTable(copy = copy, caption = copy.text("adminOffers.list.caption"), offers = offers)
    @endif
`)
