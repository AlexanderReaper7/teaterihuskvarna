@import se.teaterihuskvarna.web.Copy
@import se.teaterihuskvarna.web.CsrfField
@import se.teaterihuskvarna.web.Feedback
@import se.teaterihuskvarna.web.LoginPage
@param Copy copy
@param CsrfField csrf
@param LoginPage page
@param String token
@param Feedback feedback
@template.layout(copy = copy, title = copy.text(page.headingKey()), content = @`
    <h1>${copy.text(page.headingKey())}</h1>
    <p>${copy.text("login.link.intro")}</p>
    @template.tag.feedback(copy = copy, feedback = feedback)
    <%-- The script posts the form for the person. After a post that was refused as stale it is left
         to them, so a browser that keeps losing its session is not sent round in a circle. --%>
    <form method="post" action="${page.linkPath()}" data-auto-submit="${!feedback.stale()}">
        @template.tag.csrf(csrf = csrf)
        <input type="hidden" name="token" value="${token}">
        <button type="submit">${copy.text("login.link.submit")}</button>
    </form>
    <script src="/js/login-link.js" defer></script>
`)
