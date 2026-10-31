@import gg.jte.Content
@import se.teaterihuskvarna.web.Copy
@param Copy copy
@param String title
@param Content content
@template.layout(copy = copy, title = title, content = @`
    <nav class="section-nav" aria-label="${copy.text("nav.member.label")}">
        <ul>
            <li><a href="/medlem">@template.icon.layoutDashboard() ${copy.text("nav.overview")}</a></li>
            <li><a href="/medlem/kontaktuppgifter">@template.icon.idCard() ${copy.text("member.contact.heading")}</a></li>
            <li><a href="/medlem/erbjudanden">@template.icon.ticket() ${copy.text("offers.heading")}</a></li>
            <li><a href="/medlem/handlingar">@template.icon.fileText() ${copy.text("documents.heading")}</a></li>
            <li><a href="/medlem/volontar">@template.icon.handHelping() ${copy.text("shifts.heading")}</a></li>
        </ul>
    </nav>
    ${content}
`)
