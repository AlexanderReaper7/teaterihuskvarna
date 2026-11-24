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
@param List<Production> productions
@param List<BoardMember> board
@param List<LuddePrize> prizes
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
                @template.tag.card(image = production.image(), source = edit.field(production, "huvudbild"), content = @`
                    <h2 data-sanity="${edit.field(production, "title")}">${production.title()}</h2>
                    @if(first != null && last != null)
                        <p class="meta"><span data-sanity="${edit.field(production, "ar")}">${String.valueOf(first)}</span>${copy.text("productions.years.between")}<span data-sanity="${edit.field(production, "tillAr")}">${String.valueOf(last)}</span></p>
                    @elseif(first != null)
                        <p class="meta" data-sanity="${edit.field(production, "ar")}">${String.valueOf(first)}</p>
                    @endif
                    $unsafe{production.bodyHtml()}
                `)
            @endfor
            </div>
        @endif
        @if(!board.isEmpty())
            <div class="cards">
            @for(BoardMember member : board)
                !{String responsibility = member.responsibility();}
                @template.tag.card(image = member.image(), source = edit.field(member, "bild"), content = @`
                    <h2 data-sanity="${edit.field(member, "namn")}">${member.name()}</h2>
                    <p class="meta" data-sanity="${edit.field(member, "roll")}">${member.role()}</p>
                    @if(responsibility != null)
                        <p data-sanity="${edit.field(member, "ansvar")}">${responsibility}</p>
                    @endif
                `)
            @endfor
            </div>
        @endif
        @if(!prizes.isEmpty())
            <div class="cards">
            @for(LuddePrize prize : prizes)
                @template.tag.card(image = prize.image(), source = edit.field(prize, "bild"), content = @`
                    <h2 data-sanity="${edit.field(prize, "mottagare")}">${prize.recipient()}</h2>
                    <p class="meta" data-sanity="${edit.field(prize, "ar")}">${String.valueOf(prize.year())}</p>
                    $unsafe{prize.citationHtml()}
                `)
            @endfor
            </div>
        @endif
    </article>
`)
