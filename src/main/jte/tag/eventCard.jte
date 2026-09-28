@import se.teaterihuskvarna.content.Event
@import se.teaterihuskvarna.content.Series
@import se.teaterihuskvarna.web.Copy
@param Copy copy
@param Event event
@param int level = 3
!{String place = event.place();}
!{Series series = event.series();}
!{String summary = event.summary();}
<article class="card">
    @template.tag.image(image = event.image(), kind = "card-picture")
    @if(level == 2)
        <h2><a href="/evenemang/${event.slug()}">${event.title()}</a></h2>
    @else
        <h3><a href="/evenemang/${event.slug()}">${event.title()}</a></h3>
    @endif
    <p class="meta"><time datetime="${event.startsAt().toString()}">${copy.dateTime(event.startsAt())}</time>@if(place != null), ${place}@endif</p>
    @if(series != null)
        <p class="meta">${copy.text("event.series", series.title())}</p>
    @endif
    @if(summary != null)
        <p>${summary}</p>
    @endif
</article>
