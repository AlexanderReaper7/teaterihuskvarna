@import se.teaterihuskvarna.content.Event
@import se.teaterihuskvarna.web.Copy
@param Copy copy
@param Event event
!{String place = event.place();}
!{String summary = event.summary();}
!{String ticketUrl = event.ticketUrl();}
<section class="next-event" aria-labelledby="nasta">
    <h2 id="nasta">${copy.text("home.next")}</h2>
    @template.tag.image(image = event.image(), kind = "hero")
    <h3><a href="/evenemang/${event.slug()}">${event.title()}</a></h3>
    <p class="meta"><time datetime="${event.startsAt().toString()}">${copy.dateTime(event.startsAt())}</time>@if(place != null), ${place}@endif</p>
    @if(summary != null)
        <p>${summary}</p>
    @endif
    @if(ticketUrl != null)
        <p><a class="button" href="${ticketUrl}">${copy.text("event.tickets")}</a></p>
    @endif
</section>
