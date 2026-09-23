@import se.teaterihuskvarna.web.Copy
@import se.teaterihuskvarna.web.CsrfField
@import se.teaterihuskvarna.web.LoginPage
@param Copy copy
@param CsrfField csrf
@param boolean stale
@param LoginPage page
@param boolean failed
@template.layout(copy = copy, title = copy.text(page.headingKey()), content = @`
    <h1>${copy.text(page.headingKey())}</h1>
    @if(stale)
        <p role="alert">${copy.text("form.stale")}</p>
    @endif
    @if(failed)
        <p role="alert">${copy.text("login.failed")}</p>
    @endif
    <p>${copy.text("login.intro")}</p>
    <form method="post" action="${page.path()}">
        @template.tag.csrf(csrf = csrf)
        <p>
            <label for="email">${copy.text("login.email")}</label>
            <input id="email" name="email" type="email" autocomplete="email webauthn" required>
        </p>
        <button type="submit">${copy.text("login.submit")}</button>
    </form>
    <section data-passkey-login data-options="${page.passkeys().loginOptions()}"
            data-login="${page.passkeys().login()}" data-error="${copy.text("passkey.error.login")}" hidden>
        <p>${copy.text("login.passkey.intro")}</p>
        <p role="alert" data-passkey-error hidden></p>
        <button type="button" class="button-secondary" data-passkey-login-button>
            ${copy.text("login.passkey.submit")}
        </button>
    </section>
    <script src="/js/passkey.js" defer></script>
`)
