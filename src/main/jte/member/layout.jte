@import gg.jte.Content
@import se.teaterihuskvarna.web.Copy
@import se.teaterihuskvarna.web.Navigation
@param Copy copy
@param String title
@param Content content
@template.layout(copy = copy, title = title, content = @`
    <nav class="section-nav" aria-label="${copy.text("nav.member.label")}">
        <ul>
            <li><a href="/medlem" aria-current="${Navigation.exactly("/medlem")}">@template.icon.layoutDashboard() ${copy.text("member.heading")}</a></li>
            <li><a href="/medlem/kontaktuppgifter" aria-current="${Navigation.current("/medlem/kontaktuppgifter")}">@template.icon.idCard() ${copy.text("member.contact.heading")}</a></li>
            <li><a href="/medlem/hushall" aria-current="${Navigation.current("/medlem/hushall")}">@template.icon.house() ${copy.text("member.household")}</a></li>
            <li><a href="/medlem/erbjudanden" aria-current="${Navigation.current("/medlem/erbjudanden")}">@template.icon.ticket() ${copy.text("offers.heading")}</a></li>
            <li><a href="/medlem/handlingar" aria-current="${Navigation.current("/medlem/handlingar")}">@template.icon.fileText() ${copy.text("documents.heading")}</a></li>
            <li><a href="/medlem/volontar" aria-current="${Navigation.current("/medlem/volontar")}">@template.icon.handHelping() ${copy.text("shifts.heading")}</a></li>
        </ul>
    </nav>
    ${content}
`)
