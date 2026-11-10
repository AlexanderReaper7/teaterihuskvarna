@import se.teaterihuskvarna.document.DocumentSummary
@import se.teaterihuskvarna.web.Copy
@import se.teaterihuskvarna.web.CsrfField
@param Copy copy
@param CsrfField csrf
@param DocumentSummary document
@param boolean stale
@template.admin.layout(copy = copy, title = copy.text("adminDocuments.delete.heading"), content = @`
    <h1>${copy.text("adminDocuments.delete.heading")}</h1>
    @if(stale)
        <p role="alert">${copy.text("form.stale")}</p>
    @endif
    <p>${copy.text("adminDocuments.delete.question", document.title())}</p>
    <p>${copy.text("adminDocuments.delete.what", copy.text("document.kind." + document.kind().name()), copy.date(document.publishedOn()))}</p>
    <p>${copy.text("adminDocuments.delete.final")}</p>
    <div class="buttons">
        <form method="post" action="/admin/handlingar/${document.id()}/ta-bort">
            @template.tag.csrf(csrf = csrf)
            <button type="submit">@template.icon.trash2() ${copy.text("adminDocuments.delete.submit")}</button>
        </form>
    </div>
    <p><a href="/admin/handlingar">${copy.text("adminDocuments.delete.cancel")}</a></p>
`)
