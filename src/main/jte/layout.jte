@import gg.jte.Content
@import se.teaterihuskvarna.web.Copy
@param Copy copy
@param String title
@param Content content
@param boolean preview = false
<!DOCTYPE html>
<html lang="sv">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <meta name="color-scheme" content="light">
    <title>@if(title == null)${copy.text("site.name")}@else${copy.text("site.pageTitle", title)}@endif</title>
    <link rel="icon" href="/img/favicon.png" type="image/png">
    <link rel="stylesheet" href="/css/site.css">
    <script src="/js/forms.js" defer></script>
    <script src="/js/menu.js" defer></script>
</head>
<body>
<a class="skip-link" href="#innehall">${copy.text("site.skip")}</a>
@if(preview)
    <div class="preview-banner" role="status">
        <p>${copy.text("preview.banner")} <a href="/forhandsgranska/avsluta">${copy.text("preview.end")}</a></p>
    </div>
@endif
<header class="site-header">
    <a href="/" class="site-logo"><img src="/img/logo-negative.png" width="1534" height="1444" alt="${copy.text("site.logo.alt")}"></a>
    <button type="button" class="menu-button" aria-controls="huvudmeny" aria-expanded="false"
            aria-label="${copy.text("nav.menu")}" hidden>
        <svg class="menu-icon-open" aria-hidden="true" focusable="false" width="24" height="24" viewBox="0 0 24 24"
             fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M4 6h16M4 12h16M4 18h16"/></svg>
        <svg class="menu-icon-close" aria-hidden="true" focusable="false" width="24" height="24" viewBox="0 0 24 24"
             fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M6 6l12 12M18 6L6 18"/></svg>
    </button>
    <nav id="huvudmeny" class="site-nav" aria-label="${copy.text("nav.label")}">
        <ul>
            <li><a href="/kalender">${copy.text("nav.calendar")}</a></li>
            <li><a href="/nyheter">${copy.text("nav.news")}</a></li>
            <li><a href="/produktioner">${copy.text("nav.productions")}</a></li>
            <li><a href="/ludde-priser">${copy.text("nav.ludde")}</a></li>
            <li><a href="/om-foreningen">${copy.text("nav.about")}</a></li>
            <li><a href="/styrelsen">${copy.text("nav.board")}</a></li>
            <li><a href="/partners">${copy.text("nav.partners")}</a></li>
            <li><a href="/kontakt">${copy.text("nav.contact")}</a></li>
            <li><a href="/bli-medlem">${copy.text("nav.join")}</a></li>
            <li><a href="/medlem">${copy.text("nav.member")}</a></li>
        </ul>
    </nav>
</header>
<main id="innehall">
${content}
</main>
<footer class="site-footer">
    <p>${copy.text("site.association")} <a href="/admin">${copy.text("nav.admin")}</a></p>
</footer>
</body>
</html>
