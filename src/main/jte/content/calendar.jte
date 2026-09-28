@import java.util.List
@import se.teaterihuskvarna.content.Event
@import se.teaterihuskvarna.content.Perspective
@import se.teaterihuskvarna.content.Series
@import se.teaterihuskvarna.web.Copy
@param Copy copy
@param Perspective perspective
@param List<Event> events
@param List<Series> series
@param String chosen
@template.layout(copy = copy, title = copy.text("calendar.title"), preview = perspective == Perspective.DRAFTS, content = @`
    <h1>${copy.text("calendar.title")}</h1>
    @if(!series.isEmpty())
        <form method="get" action="/kalender" class="filter">
            <label for="serie">${copy.text("calendar.series")}</label>
            <select id="serie" name="serie">
                <option value="">${copy.text("calendar.allSeries")}</option>
                @for(Series one : series)
                    <option value="${one.slug()}" selected="${one.slug().equals(chosen)}">${one.title()}</option>
                @endfor
            </select>
            <button type="submit">${copy.text("calendar.filter")}</button>
        </form>
    @endif
    @if(events.isEmpty())
        <p>${copy.text("calendar.empty")}</p>
    @else
        <div class="cards">
        @for(Event event : events)
            @template.tag.eventCard(copy = copy, event = event, level = 2)
        @endfor
        </div>
    @endif
`)
