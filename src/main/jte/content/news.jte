@import java.util.List
@import se.teaterihuskvarna.content.NewsItem
@import se.teaterihuskvarna.content.Perspective
@import se.teaterihuskvarna.web.Copy
@param Copy copy
@param Perspective perspective
@param List<NewsItem> news
@template.layout(copy = copy, title = copy.text("news.title"), preview = perspective == Perspective.DRAFTS, content = @`
    <h1>${copy.text("news.title")}</h1>
    @if(news.isEmpty())
        <p>${copy.text("news.empty")}</p>
    @else
        <div class="cards">
        @for(NewsItem item : news)
            @template.tag.newsCard(copy = copy, item = item, level = 2)
        @endfor
        </div>
    @endif
`)
