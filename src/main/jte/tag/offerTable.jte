@import java.util.List
@import se.teaterihuskvarna.offer.OfferDetails
@import se.teaterihuskvarna.web.Copy
@param Copy copy
@param String caption
@param List<OfferDetails> offers
<table>
    <caption>${caption}</caption>
    <thead>
        <tr>
            <th scope="col">${copy.text("adminOffers.list.title")}</th>
            <th scope="col">${copy.text("adminOffers.list.startsAt")}</th>
            <th scope="col">${copy.text("adminOffers.list.registered")}</th>
            <th scope="col">${copy.text("offers.placesLeft")}</th>
            <th scope="col">${copy.text("adminOffers.list.state")}</th>
        </tr>
    </thead>
    <tbody>
        @for(OfferDetails offer : offers)
            <tr>
                <th scope="row"><a href="/admin/erbjudanden/${offer.id()}">${offer.title()}</a></th>
                <td>@if(offer.startsAt() != null)${copy.dateTime(offer.startsAt())}@endif</td>
                <td>@if(offer.capacity() == null)${offer.registered()}@else${copy.text("adminOffers.list.ofCapacity", offer.registered(), offer.capacity())}@endif</td>
                <td>@if(offer.placesLeft() == null)${copy.text("offers.unlimited")}@else${offer.placesLeft()}@endif</td>
                <td>@if(offer.published())${copy.text("adminOffers.state.published")}@else${copy.text("adminOffers.state.draft")}@endif</td>
            </tr>
        @endfor
    </tbody>
</table>
