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
        <table>
            <caption>${copy.text("adminOffers.list.caption")}</caption>
            <thead>
                <tr>
                    <th scope="col">${copy.text("adminOffers.list.title")}</th>
                    <th scope="col">${copy.text("adminOffers.list.startsAt")}</th>
                    <th scope="col">${copy.text("adminOffers.list.registered")}</th>
                    <th scope="col">${copy.text("adminOffers.list.state")}</th>
                </tr>
            </thead>
            <tbody>
                @for(OfferDetails offer : offers)
                    <tr>
                        <th scope="row"><a href="/admin/erbjudanden/${offer.id()}">${offer.title()}</a></th>
                        <td>@if(offer.startsAt() != null)${copy.dateTime(offer.startsAt())}@endif</td>
                        <td>@if(offer.capacity() == null)${offer.registered()}@else${copy.text("adminOffers.list.ofCapacity", offer.registered(), offer.capacity())}@endif</td>
                        <td>@if(offer.published())${copy.text("adminOffers.state.published")}@else${copy.text("adminOffers.state.draft")}@endif</td>
                    </tr>
                @endfor
            </tbody>
        </table>
    @endif
`)
