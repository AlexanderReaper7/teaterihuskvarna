@import java.util.List
@import se.teaterihuskvarna.content.EditAttributes
@import se.teaterihuskvarna.content.Event
@import se.teaterihuskvarna.content.NewsItem
@import se.teaterihuskvarna.content.Perspective
@import se.teaterihuskvarna.web.Copy
@import se.teaterihuskvarna.web.PageMetadata
@param Copy copy
@param PageMetadata metadata
@param Perspective perspective
@param EditAttributes edit
@param Event next
@param List<Event> events
@param List<NewsItem> news
@template.layout(metadata = metadata, copy = copy, title = null, preview = perspective == Perspective.DRAFTS, content = @`
    <h1>${copy.text("home.title")}</h1>
    @if(next == null)
        <p>${copy.text("home.noEvents")}</p>
    @else
        @template.tag.nextEvent(copy = copy, event = next, edit = edit)
    @endif
    @if(!events.isEmpty())
        <section aria-labelledby="kommande">
            <h2 id="kommande">${copy.text("home.upcoming")}</h2>
            <div class="cards">
            @for(Event event : events)
                @template.tag.eventCard(copy = copy, event = event, edit = edit)
            @endfor
            </div>
            <p><a href="/kalender">${copy.text("home.calendar")}</a></p>
        </section>
    @endif
    @if(!news.isEmpty())
        <section aria-labelledby="senaste">
            <h2 id="senaste">${copy.text("home.news")}</h2>
            <div class="cards">
            @for(NewsItem item : news)
                @template.tag.newsCard(copy = copy, item = item, edit = edit)
            @endfor
            </div>
            <p><a href="/nyheter">${copy.text("home.allNews")}</a></p>
        </section>
    @endif
`)
