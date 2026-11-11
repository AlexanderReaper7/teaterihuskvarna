@import se.teaterihuskvarna.web.Copy
@import se.teaterihuskvarna.web.CsrfField
@import se.teaterihuskvarna.web.Feedback
@import se.teaterihuskvarna.web.LoginPage
@param Copy copy
@param CsrfField csrf
@param LoginPage page
@param String token
@param Feedback feedback
<%-- The script posts the form for the person. After a post that was refused as stale it is left
     to them, so a browser that keeps losing its session is not sent round in a circle. --%>
!{boolean auto = !feedback.stale();}
@template.layout(copy = copy, title = copy.text(auto ? "login.link.heading" : page.headingKey()), content = @`
    <%-- The page is on screen until the login's answer comes back, about a second on a phone, so it
         says what is happening rather than looking like the login page (#214). site.css keeps the
         form's line and button hidden for a few seconds while the script sends it. --%>
    <h1>${copy.text(auto ? "login.link.heading" : page.headingKey())}</h1>
    @template.tag.feedback(copy = copy, feedback = feedback)
    <form method="post" action="${page.linkPath()}" data-auto-submit="${auto}">
        @template.tag.csrf(csrf = csrf)
        <input type="hidden" name="token" value="${token}">
        @if(auto)
            <p>${copy.text("login.link.fallback")}</p>
        @endif
        <button type="submit">${copy.text("login.link.submit")}</button>
    </form>
    <script src="/js/login-link.js" defer></script>
`)
