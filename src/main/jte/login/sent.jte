@import se.teaterihuskvarna.web.Copy
@import se.teaterihuskvarna.web.CsrfField
@import se.teaterihuskvarna.web.LoginPage
@param Copy copy
@param CsrfField csrf
@param LoginPage page
@param String lifetime
@param boolean failed
@template.layout(copy = copy, title = copy.text("login.sent.heading"), content = @`
    <h1>${copy.text("login.sent.heading")}</h1>
    @if(failed)
        <p role="alert">${copy.text("login.code.failed")}</p>
    @endif
    <p>${copy.text("login.sent.body", lifetime)}</p>
    <p>${copy.text("login.sent.spam")}</p>
    <form method="post" action="${page.linkPath()}">
        @template.tag.csrf(csrf = csrf)
        <p>${copy.text("login.code.intro")}</p>
        <p>
            <label for="code">${copy.text("login.code.label")}</label>
            <input id="code" name="code" type="text" inputmode="numeric" autocomplete="one-time-code"
                    maxlength="12" required>
        </p>
        <button type="submit">${copy.text("login.code.submit")}</button>
    </form>
    <p><a href="${page.path()}">${copy.text("login.sent.again")}</a></p>
`)
