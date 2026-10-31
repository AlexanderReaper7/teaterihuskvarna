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
@import se.teaterihuskvarna.offer.MemberOffer
@import se.teaterihuskvarna.volunteer.MemberShift
@import se.teaterihuskvarna.web.Copy
@import se.teaterihuskvarna.web.CsrfField
@import se.teaterihuskvarna.web.DeviceUrls
@param Copy copy
@param CsrfField csrf
@param MemberDetails member
@param HouseholdDetails household = null
@param List<MemberOffer> registrations
@param List<MemberShift> shifts
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
    <h2>${copy.text("fee.heading", String.valueOf(member.fee().year()))}
        @template.tag.feeBadge(copy = copy, paid = paidAt != null)</h2>
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
        <h3>${copy.text("fee.guide.heading")}</h3>
        <ol>
            <li>${copy.text("fee.guide.step1", payment.message())}</li>
            <li>${copy.text("fee.guide.step2", payment.bankgiro(), payment.message())}</li>
            <li>${copy.text("fee.guide.step3")}</li>
            <li>${copy.text("fee.guide.step4")}</li>
        </ol>
    @endif

    <h2>${copy.text("member.registrations.heading")}</h2>
    @if(registrations.isEmpty())
        <p>${copy.text("member.registrations.none")} <a href="/medlem/erbjudanden">${copy.text("member.registrations.find")}</a></p>
    @else
        <ul>
            @for(MemberOffer offer : registrations)
                <li><a href="/medlem/erbjudanden/${offer.id()}">${offer.title()}</a>@if(offer.startsAt() != null), ${copy.dateTime(offer.startsAt())}@endif</li>
            @endfor
        </ul>
    @endif

    <h2>${copy.text("member.shifts.heading")}</h2>
    @if(shifts.isEmpty())
        <p>${copy.text("member.shifts.none")} <a href="/medlem/volontar">${copy.text("member.shifts.find")}</a></p>
    @else
        <ul>
            @for(MemberShift shift : shifts)
                <li>${copy.text("member.shifts.item", copy.text("shift.task." + shift.task().name()), shift.eventTitle(),
                        copy.text("shifts.between", copy.dateTime(shift.startsAt()), copy.time(shift.endsAt())))}</li>
            @endfor
        </ul>
        <p><a href="/medlem/volontar">${copy.text("member.shifts.manage")}</a></p>
    @endif

    <h2>${copy.text("member.contact.heading")}</h2>
    <dl class="details">
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

    <h2>${copy.text("member.household")}</h2>
    @if(household == null)
        <p>${copy.text("member.noHousehold")}</p>
        <p><a href="/medlem/hushall">${copy.text("household.create")}</a></p>
    @else
        <p>${household.name()}</p>
        <p><a href="/medlem/hushall">${copy.text("member.household.manage")}</a></p>
        <ul>
            @for(HouseholdMember other : household.members())
                <li>
                    ${other.fullName()}
                    @if(other.id().equals(household.ownerMemberId()))
                        ${copy.text("household.isOwner")}
                    @endif
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
