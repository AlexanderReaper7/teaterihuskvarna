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
        <p>${copy.text("fee.message", payment.message())}</p>
    @endif

    <h2>${copy.text("member.household")}</h2>
    @if(household == null)
        <p>${copy.text("member.noHousehold")}</p>
    @else
        <p>${household.name()}</p>
        <ul>
            @for(HouseholdMember other : household.members())
                <li>
                    ${other.fullName()}
                    @if(other.hasAccount())
                        ${copy.text("household.hasAccount")}
                    @elseif(!other.id().equals(member.id()))
                        @if(other.invitationExpires() != null)
                            ${copy.text("household.invited", copy.dateTime(other.invitationExpires()))}
                        @endif
                        <form method="post" action="/medlem/hushall/${other.id()}/inbjudan">
                            @template.tag.csrf(csrf = csrf)
                            !{String fieldId = "invite-" + other.id();}
                            <label for="${fieldId}">${copy.text("invitation.email", other.fullName())}</label>
                            <input id="${fieldId}" name="email" type="email" autocomplete="off" required>
                            <button type="submit">${copy.text("invitation.send")}</button>
                        </form>
                    @endif
                </li>
            @endfor
        </ul>
    @endif

    @template.tag.passkeys(copy = copy, csrf = csrf, passkeys = passkeys, urls = passkeyUrls)
    @template.tag.devices(copy = copy, csrf = csrf, devices = devices, urls = deviceUrls)
    @template.tag.logout(copy = copy, csrf = csrf, action = "/logga-ut")
    @template.tag.loginCountdown(copy = copy, status = loginStatus, urls = deviceUrls)
    <script src="/js/passkey.js" defer></script>
`)
