@import gg.jte.Content
@import se.teaterihuskvarna.web.Copy
@param Copy copy
@param String title
@param Content content
@template.layout(copy = copy, title = title, content = @`
    <nav class="section-nav" aria-label="${copy.text("nav.member.label")}">
        <ul>
            <li><a href="/medlem">${copy.text("nav.overview")}</a></li>
            <li><a href="/medlem/kontaktuppgifter">${copy.text("member.contact.heading")}</a></li>
            <li><a href="/medlem/erbjudanden">${copy.text("offers.heading")}</a></li>
            <li><a href="/medlem/handlingar">${copy.text("documents.heading")}</a></li>
            <li><a href="/medlem/volontar">${copy.text("shifts.heading")}</a></li>
        </ul>
    </nav>
    ${content}
`)
