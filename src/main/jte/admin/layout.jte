@import gg.jte.Content
@import se.teaterihuskvarna.web.Copy
@param Copy copy
@param String title
@param Content content
@template.layout(copy = copy, title = title, content = @`
    <nav class="section-nav" aria-label="${copy.text("nav.admin.label")}">
        <ul>
            <li><a href="/admin">${copy.text("nav.overview")}</a></li>
            <li><a href="/admin/medlemmar">${copy.text("register.heading")}</a></li>
            <li><a href="/admin/hushall">${copy.text("household.heading")}</a></li>
            <li><a href="/admin/erbjudanden">${copy.text("adminOffers.heading")}</a></li>
            <li><a href="/admin/handlingar">${copy.text("adminDocuments.heading")}</a></li>
            <li><a href="/admin/volontarpass">${copy.text("adminShifts.heading")}</a></li>
            <li><a href="/admin/utskick">${copy.text("adminMailings.heading")}</a></li>
        </ul>
    </nav>
    ${content}
`)
