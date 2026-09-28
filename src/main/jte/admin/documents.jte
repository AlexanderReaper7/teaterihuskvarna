@import java.util.Objects
@import java.util.List
@import se.teaterihuskvarna.document.DocumentForm
@import se.teaterihuskvarna.document.DocumentKind
@import se.teaterihuskvarna.document.DocumentSummary
@import se.teaterihuskvarna.web.Copy
@import se.teaterihuskvarna.web.CsrfField
@import se.teaterihuskvarna.web.FieldErrors
@param Copy copy
@param CsrfField csrf
@param List<DocumentSummary> documents
@param DocumentForm form
@param FieldErrors errors
@param String fileError
@param String error
@param String notice
!{String kindError = errors.message("kind");}
!{String kindInvalid = kindError == null ? null : "true";}
!{String kindDescribedBy = kindError == null ? null : "kind-error";}
!{String fileInvalid = fileError == null ? null : "true";}
!{String fileDescribedBy = fileError == null ? null : "file-error";}
@template.layout(copy = copy, title = copy.text("adminDocuments.heading"), content = @`
    <h1>${copy.text("adminDocuments.heading")}</h1>
    <p><a href="/admin">${copy.text("admin.toOverview")}</a></p>
    @if(error != null)
        <p role="alert">${error}</p>
    @endif
    @if(notice != null)
        <p role="status">${notice}</p>
    @endif

    @if(documents.isEmpty())
        <p>${copy.text("adminDocuments.none")}</p>
    @else
        <table>
            <caption>${copy.text("adminDocuments.list.caption")}</caption>
            <thead>
                <tr>
                    <th scope="col">${copy.text("adminDocuments.list.title")}</th>
                    <th scope="col">${copy.text("adminDocuments.list.kind")}</th>
                    <th scope="col">${copy.text("adminDocuments.list.publishedOn")}</th>
                    <th scope="col">${copy.text("adminDocuments.list.remove")}</th>
                </tr>
            </thead>
            <tbody>
                @for(DocumentSummary document : documents)
                    !{String removeLabel = copy.text("adminDocuments.remove.label", document.title());}
                    <tr>
                        <th scope="row"><a href="/admin/handlingar/${document.id()}/fil">${document.title()}</a></th>
                        <td>${copy.text("document.kind." + document.kind().name())}</td>
                        <td>${copy.date(document.publishedOn())}</td>
                        <td>
                            <form method="post" action="/admin/handlingar/${document.id()}/ta-bort">
                                @template.tag.csrf(csrf = csrf)
                                <button type="submit" class="button-secondary"
                                        aria-label="${removeLabel}">
                                    ${copy.text("adminDocuments.remove")}
                                </button>
                            </form>
                        </td>
                    </tr>
                @endfor
            </tbody>
        </table>
    @endif

    <h2>${copy.text("adminDocuments.upload.heading")}</h2>
    @template.tag.errors(copy = copy, errors = errors)
    @if(fileError != null)
        <div role="alert"><p><a href="#file">${fileError}</a></p></div>
    @endif
    <form method="post" action="/admin/handlingar" enctype="multipart/form-data">
        @template.tag.csrf(csrf = csrf)
        @template.tag.field(name = "title", label = copy.text("adminDocuments.upload.title"), type = "text",
                autocomplete = "off", value = form.title(), error = errors.message("title"), required = true)
        <p>
            <label for="kind">${copy.text("adminDocuments.upload.kind")}</label>
            <select id="kind" name="kind" required aria-invalid="${kindInvalid}" aria-describedby="${kindDescribedBy}">
                <option value="">${copy.text("adminDocuments.upload.kind.choose")}</option>
                @for(DocumentKind kind : DocumentKind.values())
                    <option value="${kind.name()}" selected="${kind == form.kind()}">${copy.text("document.kind." + kind.name())}</option>
                @endfor
            </select>
            @if(kindError != null)
                <span id="kind-error" class="field-error">${kindError}</span>
            @endif
        </p>
        @template.tag.field(name = "publishedOn", label = copy.text("adminDocuments.upload.publishedOn"), type = "date",
                autocomplete = "off", value = Objects.toString(form.publishedOn(), null),
                error = errors.message("publishedOn"), required = true)
        <p>
            <label for="file">${copy.text("adminDocuments.upload.file")}</label>
            <input id="file" name="file" type="file" accept="application/pdf,.pdf" required
                   aria-invalid="${fileInvalid}" aria-describedby="${fileDescribedBy}">
            @if(fileError != null)
                <span id="file-error" class="field-error">${fileError}</span>
            @endif
        </p>
        <p>${copy.text("adminDocuments.upload.limit")}</p>
        <button type="submit">${copy.text("adminDocuments.upload.submit")}</button>
    </form>
`)
