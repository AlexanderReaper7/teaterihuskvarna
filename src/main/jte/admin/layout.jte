@import gg.jte.Content
@import se.teaterihuskvarna.web.Copy
@param Copy copy
@param String title
@param Content content
@template.layout(copy = copy, title = title, content = @`
    <nav class="section-nav" aria-label="${copy.text("nav.admin.label")}">
        <ul>
            <li><a href="/admin">@template.icon.layoutDashboard() ${copy.text("nav.overview")}</a></li>
            <li><a href="/admin/medlemmar">@template.icon.contact() ${copy.text("register.heading")}</a></li>
            <li><a href="/admin/hushall">@template.icon.house() ${copy.text("household.heading")}</a></li>
            <li><a href="/admin/erbjudanden">@template.icon.ticket() ${copy.text("adminOffers.heading")}</a></li>
            <li><a href="/admin/handlingar">@template.icon.fileText() ${copy.text("adminDocuments.heading")}</a></li>
            <li><a href="/admin/volontarpass">@template.icon.handHelping() ${copy.text("adminShifts.heading")}</a></li>
            <li><a href="/admin/utskick">@template.icon.mail() ${copy.text("adminMailings.heading")}</a></li>
        </ul>
    </nav>
    ${content}
`)
