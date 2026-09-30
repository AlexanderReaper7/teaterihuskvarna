@import se.teaterihuskvarna.content.Event
@import se.teaterihuskvarna.content.Image
@import se.teaterihuskvarna.content.Perspective
@import se.teaterihuskvarna.content.Series
@import se.teaterihuskvarna.web.Copy
@param Copy copy
@param Perspective perspective
@param Event event
!{String place = event.place();}
!{Series series = event.series();}
!{String summary = event.summary();}
!{String ticketUrl = event.ticketUrl();}
@template.layout(copy = copy, title = event.title(), preview = perspective == Perspective.DRAFTS, content = @`
    <article>
        <h1>${event.title()}</h1>
        <p class="meta"><time datetime="${event.startsAt().toString()}">${copy.dateTime(event.startsAt())}</time>@if(place != null), ${place}@endif</p>
        @if(series != null)
            <p class="meta"><a href="/kalender?serie=${series.slug()}">${copy.text("event.series", series.title())}</a></p>
        @endif
        @template.tag.image(image = event.image(), kind = "hero")
        @if(summary != null)
            <p class="ingress">${summary}</p>
        @endif
        $unsafe{event.descriptionHtml()}
        @if(ticketUrl != null)
            <p><a class="button" href="${ticketUrl}">${copy.text("event.tickets")}</a></p>
        @endif
        @if(!event.gallery().isEmpty())
            <h2>${copy.text("event.gallery")}</h2>
            <div class="gallery">
            @for(Image picture : event.gallery())
                @template.tag.image(image = picture)
            @endfor
            </div>
        @endif
    </article>
`)
