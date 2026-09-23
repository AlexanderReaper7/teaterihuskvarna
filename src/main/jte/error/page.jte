@import se.teaterihuskvarna.web.Copy
@param Copy copy
@param boolean notFound
!{String heading = copy.text(notFound ? "error.notFound.heading" : "error.other.heading");}
@template.layout(copy = copy, title = heading, content = @`
    <h1>${heading}</h1>
    <p>${copy.text(notFound ? "error.notFound.body" : "error.other.body")}</p>
    <p><a href="/">${copy.text("error.home")}</a></p>
`)
