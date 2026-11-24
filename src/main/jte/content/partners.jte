@import java.util.List
@import se.teaterihuskvarna.content.EditAttributes
@import se.teaterihuskvarna.content.Partner
@import se.teaterihuskvarna.content.Perspective
@import se.teaterihuskvarna.web.Copy
@import se.teaterihuskvarna.web.PageMetadata
@param Copy copy
@param PageMetadata metadata
@param Perspective perspective
@param EditAttributes edit
@param List<Partner> partners
@template.layout(metadata = metadata, copy = copy, title = copy.text("partners.title"), preview = perspective == Perspective.DRAFTS, content = @`
    <h1>${copy.text("partners.title")}</h1>
    @if(partners.isEmpty())
        <p>${copy.text("partners.empty")}</p>
    @else
        <div class="cards">
        @for(Partner partner : partners)
            !{String url = partner.url();}
            !{String description = partner.description();}
            @template.tag.card(image = partner.logo(), kind = "logo", source = edit.field(partner, "logotyp"), content = @`
                <h2 data-sanity="${edit.field(partner, "namn")}">@if(url != null)<a href="${url}">${partner.name()}</a>@else${partner.name()}@endif</h2>
                @if(description != null)
                    <p data-sanity="${edit.field(partner, "beskrivning")}">${description}</p>
                @endif
            `)
        @endfor
        </div>
    @endif
`)
