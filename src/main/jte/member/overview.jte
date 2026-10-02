@import java.time.Instant
@import java.util.List
@import se.teaterihuskvarna.login.DeviceDetails
@import se.teaterihuskvarna.login.LoginStatus
@import se.teaterihuskvarna.login.PasskeyDetails
@import se.teaterihuskvarna.login.PasskeyUrls
@import se.teaterihuskvarna.member.HouseholdDetails
@import se.teaterihuskvarna.member.HouseholdMember
@import se.teaterihuskvarna.member.MemberDetails
@import se.teaterihuskvarna.member.PaymentInstruction
@import se.teaterihuskvarna.web.Copy
@import se.teaterihuskvarna.web.CsrfField
@import se.teaterihuskvarna.web.DeviceUrls
@param Copy copy
@param CsrfField csrf
@param MemberDetails member
@param HouseholdDetails household = null
@param List<PasskeyDetails> passkeys
@param PasskeyUrls passkeyUrls
@param boolean offerPasskey = false
@param List<DeviceDetails> devices
@param LoginStatus loginStatus
@param DeviceUrls deviceUrls
@param String error
@param String notice
!{Instant paidAt = member.fee().paidAt();}
!{PaymentInstruction payment = member.fee().payment();}
@template.member.layout(copy = copy, title = copy.text("member.heading"), content = @`
    <h1>${copy.text("member.heading")}</h1>
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
        <dt>${copy.text("member.phone")}</dt>
        <dd>@if(member.phone() == null)${copy.text("member.missing")}@else${member.phone()}@endif</dd>
        <dt>${copy.text("member.address")}</dt>
        <dd>@if(member.address() == null)${copy.text("member.missing")}@else${member.address()}@endif</dd>
        <dt>${copy.text("member.postalCode")}</dt>
        <dd>@if(member.postalCode() == null)${copy.text("member.missing")}@else${member.postalCode()}@endif</dd>
        <dt>${copy.text("member.city")}</dt>
        <dd>@if(member.city() == null)${copy.text("member.missing")}@else${member.city()}@endif</dd>
    </dl>
    <p><a href="/medlem/kontaktuppgifter">${copy.text("member.contact.edit")}</a></p>
    <p>${copy.text("member.contact.emailNote")}</p>

    <h2>${copy.text("fee.heading", String.valueOf(member.fee().year()))}</h2>
    @if(paidAt != null)
        <p>
            ${copy.text("fee.paid", copy.date(paidAt))}
            @if(member.fee().throughHousehold())
                ${copy.text("fee.throughHousehold")}
            @endif
        </p>
    @elseif(payment != null)
        <p>${copy.text("fee.unpaid")}</p>
        <p>${copy.text("fee.howToPay", copy.kronor(payment.individualOre()),
                copy.kronor(payment.householdOre()), payment.bankgiro())}</p>
        @if(member.email() != null)
            <h3>${copy.text("fee.guide.heading")}</h3>
            <ol>
                <li>${copy.text("fee.guide.step1", member.email())}</li>
                <li>${copy.text("fee.guide.step2", payment.bankgiro(), member.email())}</li>
                <li>${copy.text("fee.guide.step3")}</li>
                <li>${copy.text("fee.guide.step4")}</li>
            </ol>
        @else
            <p>${copy.text("fee.message", payment.message())}</p>
        @endif
    @endif

    @template.tag.passkeys(copy = copy, csrf = csrf, passkeys = passkeys, urls = passkeyUrls)
    @template.tag.devices(copy = copy, csrf = csrf, devices = devices, urls = deviceUrls)
    @template.tag.logout(copy = copy, csrf = csrf, action = "/logga-ut")
    @template.tag.loginCountdown(copy = copy, status = loginStatus, urls = deviceUrls)
    <script src="/js/passkey.js" defer></script>
`)
