@import se.teaterihuskvarna.web.Copy
@import se.teaterihuskvarna.web.LoginPage
@param Copy copy
@param LoginPage page
@template.layout(copy = copy, title = copy.text("login.elsewhere.heading"), content = @`
    <h1>${copy.text("login.elsewhere.heading")}</h1>
    <p>${copy.text("login.elsewhere.body")}</p>
    <p><a href="${page.path()}">${copy.text("login.elsewhere.again")}</a></p>
`)
