@import se.teaterihuskvarna.offer.MemberOffer
@import se.teaterihuskvarna.web.Copy
@import se.teaterihuskvarna.web.CsrfField
@import se.teaterihuskvarna.web.Feedback
@param Copy copy
@param CsrfField csrf
@param MemberOffer offer
@param Feedback feedback
@template.member.layout(copy = copy, title = offer.title(), content = @`
    <h1>${offer.title()}</h1>
    <p><a href="/medlem/erbjudanden">${copy.text("offers.back")}</a></p>
    @template.tag.feedback(copy = copy, feedback = feedback)
    @if(!offer.description().isEmpty())
        <p class="preserve-lines">${offer.description()}</p>
    @endif
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
        <dt>${copy.text("offers.status")}</dt>
        <dd>@if(offer.registered())${copy.text("offers.registered")}@else${copy.text("offers.notRegistered")}@endif</dd>
    </dl>
    @if(!offer.open())
        <p>${copy.text("offers.closed")}</p>
    @elseif(offer.registered())
        <form method="post" action="/medlem/erbjudanden/${offer.id()}/avanmal">
            @template.tag.csrf(csrf = csrf)
            <button type="submit" class="button-secondary">@template.icon.x() ${copy.text("offers.cancel")}</button>
        </form>
    @elseif(offer.canRegister())
        <form method="post" action="/medlem/erbjudanden/${offer.id()}/anmal">
            @template.tag.csrf(csrf = csrf)
            <button type="submit">@template.icon.check() ${copy.text("offers.register")}</button>
        </form>
    @else
        <p>${copy.text("offers.full")}</p>
    @endif
`)
