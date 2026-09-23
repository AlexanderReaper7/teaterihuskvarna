@import se.teaterihuskvarna.web.Copy
@param Copy copy
@template.layout(copy = copy, title = null, content = @`
    <h1>${copy.text("home.title")}</h1>
    <p>${copy.text("home.intro")}</p>
`)
