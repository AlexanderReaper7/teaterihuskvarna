@import se.teaterihuskvarna.web.Copy
@import se.teaterihuskvarna.web.CsrfField
@import se.teaterihuskvarna.web.LoginPage
@param Copy copy
@param CsrfField csrf
@param LoginPage page
@param String token
@template.layout(copy = copy, title = copy.text(page.headingKey()), content = @`
    <h1>${copy.text(page.headingKey())}</h1>
    <p>${copy.text("login.link.intro")}</p>
    <form method="post" action="${page.linkPath()}" data-auto-submit>
        @template.tag.csrf(csrf = csrf)
        <input type="hidden" name="token" value="${token}">
        <button type="submit">${copy.text("login.link.submit")}</button>
    </form>
    <script src="/js/login-link.js" defer></script>
`)
