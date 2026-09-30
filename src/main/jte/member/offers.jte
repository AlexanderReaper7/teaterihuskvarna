@import java.util.List
@import se.teaterihuskvarna.offer.MemberOffer
@import se.teaterihuskvarna.web.Copy
@param Copy copy
@param List<MemberOffer> offers
@template.member.layout(copy = copy, title = copy.text("offers.heading"), content = @`
    <h1>${copy.text("offers.heading")}</h1>
    <p><a href="/medlem">${copy.text("member.toOverview")}</a></p>
    @if(offers.isEmpty())
        <p>${copy.text("offers.none")}</p>
    @else
        @for(MemberOffer offer : offers)
            <h2><a href="/medlem/erbjudanden/${offer.id()}">${offer.title()}</a></h2>
            <dl>
                @if(offer.startsAt() != null)
                    <dt>${copy.text("offers.startsAt")}</dt>
                    <dd>${copy.dateTime(offer.startsAt())}</dd>
                @endif
                @if(offer.closesAt() != null)
                    <dt>${copy.text("offers.closesAt")}</dt>
                    <dd>${copy.dateTime(offer.closesAt())}</dd>
                @endif
                <dt>${copy.text("offers.placesLeft")}</dt>
                <dd>@if(offer.placesLeft() == null)${copy.text("offers.unlimited")}@else${offer.placesLeft()}@endif</dd>
                @if(offer.registered())
                    <dt>${copy.text("offers.status")}</dt>
                    <dd>${copy.text("offers.registered")}</dd>
                @endif
            </dl>
        @endfor
    @endif
`)
