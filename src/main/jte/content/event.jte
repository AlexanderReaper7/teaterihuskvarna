@import se.teaterihuskvarna.content.EditAttributes
@import se.teaterihuskvarna.content.Event
@import se.teaterihuskvarna.content.Image
@import se.teaterihuskvarna.content.Perspective
@import se.teaterihuskvarna.content.Series
@import se.teaterihuskvarna.web.Copy
@import se.teaterihuskvarna.web.PageMetadata
@param Copy copy
@param PageMetadata metadata
@param Perspective perspective
@param Event event
@param EditAttributes edit
!{String place = event.place();}
!{Series series = event.series();}
!{String summary = event.summary();}
!{String ticketUrl = event.ticketUrl();}
@template.layout(metadata = metadata, copy = copy, title = event.title(), preview = perspective == Perspective.DRAFTS, content = @`
    <article>
        <h1 data-sanity="${edit.field(event, "title")}">${event.title()}</h1>
        <p class="meta"><time datetime="${event.startsAt().toString()}" data-sanity="${edit.field(event, "datumTid")}">${copy.dateTime(event.startsAt())}</time>@if(place != null), <span data-sanity="${edit.field(event, "plats")}">${place}</span>@endif</p>
        @if(series != null)
            <p class="meta" data-sanity="${edit.field(series, "title")}"><a href="/kalender?serie=${series.slug()}">${copy.text("event.series", series.title())}</a></p>
        @endif
        @template.tag.image(image = event.image(), kind = "hero", source = edit.field(event, "huvudbild"))
        @if(summary != null)
            <p class="ingress" data-sanity="${edit.field(event, "ingress")}">${summary}</p>
        @endif
        $unsafe{event.descriptionHtml()}
        @if(ticketUrl != null)
            <p data-sanity="${edit.field(event, "biljettlank")}"><a class="button" href="${ticketUrl}">${copy.text("event.tickets")}</a></p>
        @endif
        @if(!event.gallery().isEmpty())
            <h2>${copy.text("event.gallery")}</h2>
            <div class="gallery">
            @for(Image picture : event.gallery())
                @template.tag.image(image = picture, source = edit.item(event, "bildgalleri", picture.key()))
            @endfor
            </div>
        @endif
    </article>
`)
