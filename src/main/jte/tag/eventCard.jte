@import se.teaterihuskvarna.content.EditAttributes
@import se.teaterihuskvarna.content.Event
@import se.teaterihuskvarna.content.Series
@import se.teaterihuskvarna.web.Copy
@param Copy copy
@param Event event
@param EditAttributes edit
@param int level = 3
!{String place = event.place();}
!{Series series = event.series();}
!{String summary = event.summary();}
@template.tag.card(image = event.image(), kind = "card-picture", source = edit.field(event, "huvudbild"), content = @`
    @if(level == 2)
        <h2 data-sanity="${edit.field(event, "title")}"><a href="/evenemang/${event.slug()}">${event.title()}</a></h2>
    @else
        <h3 data-sanity="${edit.field(event, "title")}"><a href="/evenemang/${event.slug()}">${event.title()}</a></h3>
    @endif
    <p class="meta"><time datetime="${event.startsAt().toString()}" data-sanity="${edit.field(event, "datumTid")}">${copy.dateTime(event.startsAt())}</time>@if(place != null), <span data-sanity="${edit.field(event, "plats")}">${place}</span>@endif</p>
    @if(series != null)
        <p class="meta" data-sanity="${edit.field(series, "title")}">${copy.text("event.series", series.title())}</p>
    @endif
    @if(summary != null)
        <p data-sanity="${edit.field(event, "ingress")}">${summary}</p>
    @endif
`)
