@import java.util.List
@import se.teaterihuskvarna.content.BoardMember
@import se.teaterihuskvarna.content.EditAttributes
@import se.teaterihuskvarna.content.LuddePrize
@import se.teaterihuskvarna.content.Page
@import se.teaterihuskvarna.content.Perspective
@import se.teaterihuskvarna.content.Production
@import se.teaterihuskvarna.web.Copy
@import se.teaterihuskvarna.web.PageMetadata
@param Copy copy
@param PageMetadata metadata
@param Perspective perspective
@param Page page
@param EditAttributes edit
@param List<Production> productions = List.of()
@param List<BoardMember> board = List.of()
@param List<LuddePrize> prizes = List.of()
@template.layout(metadata = metadata, copy = copy, title = page.title(), preview = perspective == Perspective.DRAFTS, content = @`
    <article>
        <h1 data-sanity="${edit.field(page, "title")}">${page.title()}</h1>
        @template.tag.image(image = page.image(), kind = "hero", source = edit.field(page, "headerbild"))
        $unsafe{page.bodyHtml()}
        @if(!productions.isEmpty())
            <div class="cards posters">
            @for(Production production : productions)
                !{Integer first = production.firstYear();}
                !{Integer last = production.lastYear();}
                <article class="card">
                    @template.tag.image(image = production.image(), source = edit.field(production, "huvudbild"))
                    <h2 data-sanity="${edit.field(production, "title")}">${production.title()}</h2>
                    @if(first != null)
                        <p class="meta" data-sanity="${edit.field(production, "ar")}">@if(last != null)${copy.text("productions.years", String.valueOf(first), String.valueOf(last))}@else${String.valueOf(first)}@endif</p>
                    @endif
                    $unsafe{production.bodyHtml()}
                </article>
            @endfor
            </div>
        @endif
        @if(!board.isEmpty())
            <div class="cards">
            @for(BoardMember member : board)
                !{String responsibility = member.responsibility();}
                <article class="card">
                    @template.tag.image(image = member.image(), source = edit.field(member, "bild"))
                    <h2 data-sanity="${edit.field(member, "namn")}">${member.name()}</h2>
                    <p class="meta" data-sanity="${edit.field(member, "roll")}">${member.role()}</p>
                    @if(responsibility != null)
                        <p data-sanity="${edit.field(member, "ansvar")}">${responsibility}</p>
                    @endif
                </article>
            @endfor
            </div>
        @endif
        @if(!prizes.isEmpty())
            <div class="cards">
            @for(LuddePrize prize : prizes)
                <article class="card">
                    @template.tag.image(image = prize.image(), source = edit.field(prize, "bild"))
                    <h2 data-sanity="${edit.field(prize, "mottagare")}">${prize.recipient()}</h2>
                    <p class="meta" data-sanity="${edit.field(prize, "ar")}">${String.valueOf(prize.year())}</p>
                    $unsafe{prize.citationHtml()}
                </article>
            @endfor
            </div>
        @endif
    </article>
`)
