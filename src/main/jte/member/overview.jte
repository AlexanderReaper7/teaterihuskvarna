@import java.util.List
@import se.teaterihuskvarna.login.DeviceDetails
@import se.teaterihuskvarna.login.LoginStatus
@import se.teaterihuskvarna.login.PasskeyDetails
@import se.teaterihuskvarna.login.PasskeyUrls
@import se.teaterihuskvarna.member.MemberDetails
@import se.teaterihuskvarna.web.Copy
@import se.teaterihuskvarna.web.CsrfField
@import se.teaterihuskvarna.web.DeviceUrls
@param Copy copy
@param CsrfField csrf
@param MemberDetails member
@param List<PasskeyDetails> passkeys
@param PasskeyUrls passkeyUrls
@param boolean offerPasskey = false
@param List<DeviceDetails> devices
@param LoginStatus loginStatus
@param DeviceUrls deviceUrls
@param String error
@param String notice
@template.layout(copy = copy, title = copy.text("member.heading"), content = @`
    <h1>${copy.text("member.heading")}</h1>
    <ul>
        <li><a href="/medlem/erbjudanden">${copy.text("offers.heading")}</a></li>
        <li><a href="/medlem/handlingar">${copy.text("documents.heading")}</a></li>
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
    <dl>
        <dt>${copy.text("member.name")}</dt>
        <dd>${member.fullName()}</dd>
        <dt>${copy.text("member.email")}</dt>
        <dd>@if(member.email() == null)${copy.text("member.missing")}@else${member.email()}@endif</dd>
        <dt>${copy.text("member.household")}</dt>
        <dd>@if(member.household() == null)${copy.text("member.noHousehold")}@else${member.household()}@endif</dd>
    </dl>
    @template.tag.passkeys(copy = copy, csrf = csrf, passkeys = passkeys, urls = passkeyUrls)
    @template.tag.devices(copy = copy, csrf = csrf, devices = devices, urls = deviceUrls)
    @template.tag.logout(copy = copy, csrf = csrf, action = "/logga-ut")
    @template.tag.loginCountdown(copy = copy, status = loginStatus, urls = deviceUrls)
    <script src="/js/passkey.js" defer></script>
`)
