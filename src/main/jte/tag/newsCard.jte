@import se.teaterihuskvarna.content.EditAttributes
@import se.teaterihuskvarna.content.NewsItem
@import se.teaterihuskvarna.web.Copy
@param Copy copy
@param NewsItem item
@param EditAttributes edit
@param int level = 3
!{String summary = item.summary();}
<article class="card">
    @template.tag.image(image = item.image(), kind = "card-picture", source = edit.field(item, "huvudbild"))
    @if(level == 2)
        <h2 data-sanity="${edit.field(item, "title")}"><a href="/nyheter/${item.slug()}">${item.title()}</a></h2>
    @else
        <h3 data-sanity="${edit.field(item, "title")}"><a href="/nyheter/${item.slug()}">${item.title()}</a></h3>
    @endif
    <p class="meta"><time datetime="${item.publishedAt().toString()}" data-sanity="${edit.field(item, "publiceringsdatum")}">${copy.date(item.publishedAt())}</time></p>
    @if(summary != null)
        <p data-sanity="${edit.field(item, "ingress")}">${summary}</p>
    @endif
</article>
