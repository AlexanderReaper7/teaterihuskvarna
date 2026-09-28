@import java.util.List
@import se.teaterihuskvarna.document.DocumentKind
@import se.teaterihuskvarna.document.DocumentSummary
@import se.teaterihuskvarna.web.Copy
@param Copy copy
@param List<DocumentSummary> documents
@template.layout(copy = copy, title = copy.text("documents.heading"), content = @`
    <h1>${copy.text("documents.heading")}</h1>
    <p><a href="/medlem">${copy.text("member.toOverview")}</a></p>
    @for(DocumentKind kind : DocumentKind.values())
        !{List<DocumentSummary> ofKind = documents.stream().filter(d -> d.kind() == kind).toList();}
        <h2>${copy.text("document.kind." + kind.name() + ".plural")}</h2>
        @if(ofKind.isEmpty())
            <p>${copy.text("documents.none")}</p>
        @else
            <ul>
                @for(DocumentSummary document : ofKind)
                    <li>
                        <a href="/medlem/handlingar/${document.id()}/fil">${document.title()}</a>
                        ${copy.text("documents.meta", copy.date(document.publishedOn()), document.kilobytes())}
                    </li>
                @endfor
            </ul>
        @endif
    @endfor
`)
