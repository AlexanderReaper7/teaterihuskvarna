@import java.util.List
@import se.teaterihuskvarna.content.Partner
@import se.teaterihuskvarna.content.Perspective
@import se.teaterihuskvarna.web.Copy
@param Copy copy
@param Perspective perspective
@param List<Partner> partners
@template.layout(copy = copy, title = copy.text("partners.title"), preview = perspective == Perspective.DRAFTS, content = @`
    <h1>${copy.text("partners.title")}</h1>
    @if(partners.isEmpty())
        <p>${copy.text("partners.empty")}</p>
    @else
        <div class="cards">
        @for(Partner partner : partners)
            !{String url = partner.url();}
            !{String description = partner.description();}
            <article class="card">
                @template.tag.image(image = partner.logo(), kind = "logo")
                <h2>@if(url != null)<a href="${url}">${partner.name()}</a>@else${partner.name()}@endif</h2>
                @if(description != null)
                    <p>${description}</p>
                @endif
            </article>
        @endfor
        </div>
    @endif
`)
