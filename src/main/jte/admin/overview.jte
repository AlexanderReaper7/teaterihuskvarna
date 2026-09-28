@import java.util.List
@import se.teaterihuskvarna.administrator.AdministratorDetails
@import se.teaterihuskvarna.administrator.NewAdministrator
@import se.teaterihuskvarna.login.DeviceDetails
@import se.teaterihuskvarna.login.LoginStatus
@import se.teaterihuskvarna.login.PasskeyDetails
@import se.teaterihuskvarna.login.PasskeyUrls
@import se.teaterihuskvarna.login.SignedIn
@import se.teaterihuskvarna.web.Copy
@import se.teaterihuskvarna.web.CsrfField
@import se.teaterihuskvarna.web.DeviceUrls
@import se.teaterihuskvarna.web.FieldErrors
@param Copy copy
@param CsrfField csrf
@param SignedIn signedIn
@param List<AdministratorDetails> administrators
@param NewAdministrator form
@param FieldErrors errors
@param String error
@param String notice
@param List<PasskeyDetails> passkeys
@param PasskeyUrls passkeyUrls
@param boolean offerPasskey = false
@param List<DeviceDetails> devices
@param LoginStatus loginStatus
@param DeviceUrls deviceUrls
@template.layout(copy = copy, title = copy.text("admin.heading"), content = @`
    <h1>${copy.text("admin.heading")}</h1>
    <p>${copy.text("admin.signedInAs", signedIn.fullName())}</p>
    @template.tag.logout(copy = copy, csrf = csrf, action = "/admin/logga-ut")
    <ul>
        <li><a href="/admin/medlemmar">${copy.text("register.heading")}</a></li>
        <li><a href="/admin/hushall">${copy.text("household.heading")}</a></li>
        <li><a href="/admin/erbjudanden">${copy.text("adminOffers.heading")}</a></li>
        <li><a href="/admin/handlingar">${copy.text("adminDocuments.heading")}</a></li>
    </ul>
    @if(offerPasskey)
        @template.tag.passkeyOffer(copy = copy, csrf = csrf, urls = passkeyUrls)
    @endif
    @if(error != null)
        <p role="alert">${error}</p>
    @endif
    @if(notice != null)
        <p role="status">${notice}</p>
    @endif

    <table>
        <caption>${copy.text("admin.list.caption")}</caption>
        <thead>
            <tr>
                <th scope="col">${copy.text("admin.list.name")}</th>
                <th scope="col">${copy.text("admin.list.email")}</th>
                <th scope="col">${copy.text("admin.list.remove")}</th>
            </tr>
        </thead>
        <tbody>
            @for(AdministratorDetails administrator : administrators)
                !{String removeLabel = copy.text("admin.remove.label", administrator.fullName());}
                <tr>
                    <td>${administrator.fullName()}</td>
                    <td>${administrator.email()}</td>
                    <td>
                        @if(administrator.id() != signedIn.id())
                            <form method="post" action="/admin/administratorer/${administrator.id()}/ta-bort">
                                @template.tag.csrf(csrf = csrf)
                                <button type="submit" class="button-secondary" aria-label="${removeLabel}">
                                    ${copy.text("admin.remove")}
                                </button>
                            </form>
                        @endif
                    </td>
                </tr>
            @endfor
        </tbody>
    </table>

    <h2>${copy.text("admin.add.heading")}</h2>
    @template.tag.errors(copy = copy, errors = errors)
    <form method="post" action="/admin/administratorer">
        @template.tag.csrf(csrf = csrf)
        @template.tag.field(name = "fullName", label = copy.text("admin.add.fullName"), type = "text",
                autocomplete = "off", value = form.fullName(), error = errors.message("fullName"), required = true)
        @template.tag.field(name = "email", label = copy.text("admin.add.email"), type = "email",
                autocomplete = "off", value = form.email(), error = errors.message("email"), required = true)
        <button type="submit">${copy.text("admin.add.submit")}</button>
    </form>

    @template.tag.passkeys(copy = copy, csrf = csrf, passkeys = passkeys, urls = passkeyUrls)
    @template.tag.devices(copy = copy, csrf = csrf, devices = devices, urls = deviceUrls)
    @template.tag.loginCountdown(copy = copy, status = loginStatus, urls = deviceUrls)
    <script src="/js/passkey.js" defer></script>
`)
