@import se.teaterihuskvarna.content.NewsItem
@import se.teaterihuskvarna.content.Perspective
@import se.teaterihuskvarna.web.Copy
@param Copy copy
@param Perspective perspective
@param NewsItem item
!{String summary = item.summary();}
!{String videoUrl = item.videoUrl();}
@template.layout(copy = copy, title = item.title(), preview = perspective == Perspective.DRAFTS, content = @`
    <article>
        <h1>${item.title()}</h1>
        <p class="meta"><time datetime="${item.publishedAt().toString()}">${copy.date(item.publishedAt())}</time></p>
        @template.tag.image(image = item.image(), kind = "hero")
        @if(summary != null)
            <p class="ingress">${summary}</p>
        @endif
        $unsafe{item.bodyHtml()}
        @if(videoUrl != null)
            <p><a href="${videoUrl}">${copy.text("news.video")}</a></p>
        @endif
    </article>
`)
