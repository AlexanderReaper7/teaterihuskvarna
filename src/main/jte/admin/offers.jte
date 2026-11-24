@import java.util.List
@import se.teaterihuskvarna.offer.OfferDetails
@import se.teaterihuskvarna.web.Copy
@import se.teaterihuskvarna.web.Feedback
@param Copy copy
@param List<OfferDetails> offers
@param Feedback feedback
@template.admin.layout(copy = copy, title = copy.text("adminOffers.heading"), content = @`
    <h1>${copy.text("adminOffers.heading")}</h1>
    @template.tag.feedback(copy = copy, feedback = feedback)
    <p><a href="/admin/erbjudanden/nytt">@template.icon.plus() ${copy.text("adminOffers.new")}</a></p>
    @if(offers.isEmpty())
        <p>${copy.text("adminOffers.none")}</p>
    @else
        @template.tag.offerTable(copy = copy, caption = copy.text("adminOffers.list.caption"), offers = offers)
    @endif
`)
