@import java.util.List
@import se.teaterihuskvarna.content.BoardMember
@import se.teaterihuskvarna.content.LuddePrize
@import se.teaterihuskvarna.content.Page
@import se.teaterihuskvarna.content.Perspective
@import se.teaterihuskvarna.content.Production
@import se.teaterihuskvarna.web.Copy
@param Copy copy
@param Perspective perspective
@param Page page
@param List<Production> productions = List.of()
@param List<BoardMember> board = List.of()
@param List<LuddePrize> prizes = List.of()
@template.layout(copy = copy, title = page.title(), preview = perspective == Perspective.DRAFTS, content = @`
    <article>
        <h1>${page.title()}</h1>
        @template.tag.image(image = page.image(), kind = "hero")
        $unsafe{page.bodyHtml()}
        @if(!productions.isEmpty())
            <div class="cards">
            @for(Production production : productions)
                !{Integer first = production.firstYear();}
                !{Integer last = production.lastYear();}
                <article class="card">
                    @template.tag.image(image = production.image())
                    <h2>${production.title()}</h2>
                    @if(first != null)
                        <p class="meta">@if(last != null)${copy.text("productions.years", String.valueOf(first), String.valueOf(last))}@else${String.valueOf(first)}@endif</p>
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
                    @template.tag.image(image = member.image())
                    <h2>${member.name()}</h2>
                    <p class="meta">${member.role()}</p>
                    @if(responsibility != null)
                        <p>${responsibility}</p>
                    @endif
                </article>
            @endfor
            </div>
        @endif
        @if(!prizes.isEmpty())
            <div class="cards">
            @for(LuddePrize prize : prizes)
                <article class="card">
                    @template.tag.image(image = prize.image())
                    <h2>${prize.recipient()}</h2>
                    <p class="meta">${String.valueOf(prize.year())}</p>
                    $unsafe{prize.citationHtml()}
                </article>
            @endfor
            </div>
        @endif
    </article>
`)
