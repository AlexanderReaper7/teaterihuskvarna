@import se.teaterihuskvarna.web.Copy
@import se.teaterihuskvarna.web.LoginPage
@param Copy copy
@param LoginPage page
@param String lifetime
@template.layout(copy = copy, title = copy.text("login.sent.heading"), content = @`
    <h1>${copy.text("login.sent.heading")}</h1>
    <p>${copy.text("login.sent.body", lifetime)}</p>
    <p>${copy.text("login.sent.spam")}</p>
    <p><a href="${page.path()}">${copy.text("login.sent.again")}</a></p>
`)
