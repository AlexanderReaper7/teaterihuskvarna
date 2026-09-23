@import java.util.List
@import se.teaterihuskvarna.login.PasskeyDetails
@import se.teaterihuskvarna.login.PasskeyUrls
@import se.teaterihuskvarna.web.Copy
@import se.teaterihuskvarna.web.CsrfField
@param Copy copy
@param CsrfField csrf
@param List<PasskeyDetails> passkeys
@param PasskeyUrls urls
<section data-passkeys data-options="${urls.registerOptions()}" data-register="${urls.register()}"
        data-label="${copy.text("passkey.label")}" data-unknown="${copy.text("passkey.label.unknown")}"
        data-error="${copy.text("passkey.error.register")}" data-cancelled="${copy.text("passkey.error.cancelled")}"
        data-exists="${copy.text("passkey.error.exists")}">
    <h2>${copy.text("passkey.heading")}</h2>
    <p>${copy.text("passkey.intro")}</p>
    @if(passkeys.isEmpty())
        <p>${copy.text("passkey.none")}</p>
    @else
        <table>
            <caption>${copy.text("passkey.list.caption")}</caption>
            <thead>
                <tr>
                    <th scope="col">${copy.text("passkey.list.label")}</th>
                    <th scope="col">${copy.text("passkey.list.created")}</th>
                    <th scope="col">${copy.text("passkey.list.lastUsed")}</th>
                    <th scope="col">${copy.text("passkey.list.remove")}</th>
                </tr>
            </thead>
            <tbody>
                @for(PasskeyDetails passkey : passkeys)
                    <tr>
                        <td>${passkey.label()}</td>
                        <td>${copy.date(passkey.created())}</td>
                        <td>${copy.date(passkey.lastUsed())}</td>
                        <td>
                            <form method="post" action="${urls.remove(passkey.id())}">
                                @template.tag.csrf(csrf = csrf)
                                <button type="submit" class="button-secondary"
                                        aria-label="${copy.text("passkey.remove.label", passkey.label())}">
                                    ${copy.text("passkey.remove")}
                                </button>
                            </form>
                        </td>
                    </tr>
                @endfor
            </tbody>
        </table>
    @endif
    <p role="alert" data-passkey-error hidden></p>
    <p data-passkey-unsupported hidden>${copy.text("passkey.unsupported")}</p>
    <button type="button" data-passkey-add hidden>${copy.text("passkey.add")}</button>
</section>
