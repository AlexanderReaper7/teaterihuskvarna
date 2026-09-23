@import se.teaterihuskvarna.login.PasskeyUrls
@import se.teaterihuskvarna.web.Copy
@import se.teaterihuskvarna.web.CsrfField
@param Copy copy
@param CsrfField csrf
@param PasskeyUrls urls
<div data-passkey-offer hidden>
    <div role="status">
        <p>${copy.text("passkey.offer")}</p>
        <form class="buttons" method="post" action="${urls.decline()}">
            @template.tag.csrf(csrf = csrf)
            <button type="button" data-passkey-add>${copy.text("passkey.add")}</button>
            <button type="button" class="button-secondary" data-passkey-dismiss>
                ${copy.text("passkey.offer.dismiss")}
            </button>
            <button type="submit" class="button-secondary">${copy.text("passkey.offer.decline")}</button>
        </form>
    </div>
    <p role="alert" data-passkey-error hidden></p>
</div>
