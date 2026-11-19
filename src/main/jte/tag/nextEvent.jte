@import se.teaterihuskvarna.content.EditAttributes
@import se.teaterihuskvarna.content.Event
@import se.teaterihuskvarna.web.Copy
@param Copy copy
@param Event event
@param EditAttributes edit
!{String place = event.place();}
!{String summary = event.summary();}
!{String ticketUrl = event.ticketUrl();}
<section class="next-event" aria-labelledby="nasta">
    <h2 id="nasta">${copy.text("home.next")}</h2>
    @template.tag.image(image = event.image(), kind = "hero", source = edit.field(event, "huvudbild"))
    <h3 data-sanity="${edit.field(event, "title")}"><a href="/evenemang/${event.slug()}">${event.title()}</a></h3>
    <p class="meta"><time datetime="${event.startsAt().toString()}" data-sanity="${edit.field(event, "datumTid")}">${copy.dateTime(event.startsAt())}</time>@if(place != null), <span data-sanity="${edit.field(event, "plats")}">${place}</span>@endif</p>
    @if(summary != null)
        <p data-sanity="${edit.field(event, "ingress")}">${summary}</p>
    @endif
    @if(ticketUrl != null)
        <p data-sanity="${edit.field(event, "biljettlank")}"><a class="button" href="${ticketUrl}">${copy.text("event.tickets")}</a></p>
    @endif
</section>
