@import gg.jte.Content
@import se.teaterihuskvarna.web.Copy
@param Copy copy
@param String title
@param Content content
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
</head>
<body>
<header class="site-header">
    <a href="/"><img src="/img/logo-negative.png" width="1534" height="1444" alt="${copy.text("site.logo.alt")}"></a>
</header>
<main>
${content}
</main>
<footer class="site-footer">
    <p>${copy.text("site.association")}</p>
</footer>
</body>
</html>
