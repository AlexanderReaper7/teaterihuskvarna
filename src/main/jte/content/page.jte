@import se.teaterihuskvarna.content.Page
@import se.teaterihuskvarna.content.Perspective
@import se.teaterihuskvarna.web.Copy
@param Copy copy
@param Perspective perspective
@param Page page
@template.layout(copy = copy, title = page.title(), preview = perspective == Perspective.DRAFTS, content = @`
    <article>
        <h1>${page.title()}</h1>
        @template.tag.image(image = page.image(), kind = "hero")
        $unsafe{page.bodyHtml()}
    </article>
`)
