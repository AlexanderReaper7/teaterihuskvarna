@import se.teaterihuskvarna.content.NewsItem
@import se.teaterihuskvarna.web.Copy
@param Copy copy
@param NewsItem item
@param int level = 3
!{String summary = item.summary();}
<article class="card">
    @template.tag.image(image = item.image(), kind = "card-picture")
    @if(level == 2)
        <h2><a href="/nyheter/${item.slug()}">${item.title()}</a></h2>
    @else
        <h3><a href="/nyheter/${item.slug()}">${item.title()}</a></h3>
    @endif
    <p class="meta"><time datetime="${item.publishedAt().toString()}">${copy.date(item.publishedAt())}</time></p>
    @if(summary != null)
        <p>${summary}</p>
    @endif
</article>
