@import se.teaterihuskvarna.content.EditAttributes
@import se.teaterihuskvarna.content.NewsItem
@import se.teaterihuskvarna.content.Perspective
@import se.teaterihuskvarna.web.Copy
@import se.teaterihuskvarna.web.PageMetadata
@param Copy copy
@param PageMetadata metadata
@param Perspective perspective
@param NewsItem item
@param EditAttributes edit
!{String summary = item.summary();}
!{String videoUrl = item.videoUrl();}
@template.layout(metadata = metadata, copy = copy, title = item.title(), preview = perspective == Perspective.DRAFTS, content = @`
    <article>
        <h1 data-sanity="${edit.field(item, "title")}">${item.title()}</h1>
        <p class="meta"><time datetime="${item.publishedAt().toString()}" data-sanity="${edit.field(item, "publiceringsdatum")}">${copy.date(item.publishedAt())}</time></p>
        @template.tag.image(image = item.image(), kind = "hero", source = edit.field(item, "huvudbild"))
        @if(summary != null)
            <p class="ingress" data-sanity="${edit.field(item, "ingress")}">${summary}</p>
        @endif
        $unsafe{item.bodyHtml()}
        @if(videoUrl != null)
            <p data-sanity="${edit.field(item, "video")}"><a href="${videoUrl}">${copy.text("news.video")}</a></p>
        @endif
    </article>
`)
