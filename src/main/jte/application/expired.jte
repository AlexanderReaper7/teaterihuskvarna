@import se.teaterihuskvarna.web.Copy
@param Copy copy
@template.layout(copy = copy, title = copy.text("application.expired.heading"), content = @`
    <h1>${copy.text("application.expired.heading")}</h1>
    <p>${copy.text("application.expired.body")}</p>
    <p><a href="/bli-medlem">${copy.text("application.expired.again")}</a></p>
`)
