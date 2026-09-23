@import se.teaterihuskvarna.web.Copy
@param Copy copy
@template.layout(copy = copy, title = copy.text("application.sent.heading"), content = @`
    <h1>${copy.text("application.sent.heading")}</h1>
    <p>${copy.text("application.sent.body")}</p>
    <p>${copy.text("application.sent.spam")}</p>
`)
