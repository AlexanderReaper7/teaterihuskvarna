@import java.time.Instant
@import java.util.List
@import se.teaterihuskvarna.member.FeeStatus
@import se.teaterihuskvarna.member.HouseholdDetails
@import se.teaterihuskvarna.member.HouseholdMember
@import se.teaterihuskvarna.member.MemberFile
@import se.teaterihuskvarna.member.MemberForm
@import se.teaterihuskvarna.web.Copy
@import se.teaterihuskvarna.web.CsrfField
@import se.teaterihuskvarna.web.FieldErrors
@param Copy copy
@param CsrfField csrf
@param MemberFile file
@param MemberForm form
@param FieldErrors errors
@param List<HouseholdDetails> households
@param String error
@param String notice
@param boolean stale
!{long id = file.member().id();}
!{FeeStatus thisYear = file.member().fee();}
!{Instant paidAt = thisYear.paidAt();}
!{HouseholdDetails household = file.household();}
!{Instant invitationExpires = file.invitationExpires();}
@template.layout(copy = copy, title = file.member().fullName(), content = @`
    <h1>${file.member().fullName()}</h1>
    <p><a href="/admin/medlemmar">${copy.text("register.toList")}</a></p>
    @if(stale)
        <p role="alert">${copy.text("form.stale")}</p>
    @endif
    @if(error != null)
        <p role="alert">${error}</p>
    @endif
    @if(notice != null)
        <p role="status">${notice}</p>
    @endif
    <p>${copy.text("register.memberSince", copy.date(file.member().memberSince()))}</p>

    <h2>${copy.text("fee.heading", String.valueOf(thisYear.year()))}</h2>
    @if(paidAt != null)
        <p>
            ${copy.text("fee.paid", copy.date(paidAt))}
            @if(thisYear.throughHousehold())
                ${copy.text("fee.throughHousehold")}
            @endif
        </p>
        @if(!thisYear.throughHousehold())
            <form method="post" action="/admin/medlemmar/${id}/avgift/angra">
                @template.tag.csrf(csrf = csrf)
                <button type="submit" class="button-secondary">${copy.text("fee.undo")}</button>
            </form>
        @endif
    @endif
    @if(!thisYear.paid() || thisYear.throughHousehold())
        <form method="post" action="/admin/medlemmar/${id}/avgift">
            @template.tag.csrf(csrf = csrf)
            <fieldset>
                <legend>${copy.text("fee.mark")}</legend>
                <p>
                    <input id="kind-individual" name="kind" type="radio" value="INDIVIDUAL" checked>
                    <label for="kind-individual">${copy.text("fee.kind.individual")}</label>
                </p>
                <p>
                    <input id="kind-household" name="kind" type="radio" value="HOUSEHOLD">
                    <label for="kind-household">${copy.text("fee.kind.household")}</label>
                </p>
                <p>
                    <label for="kronor">${copy.text("fee.amount")}</label>
                    <input id="kronor" name="kronor" type="text" inputmode="numeric" autocomplete="off">
                </p>
                <button type="submit">${copy.text("fee.mark.submit")}</button>
            </fieldset>
        </form>
    @endif

    <h2>${copy.text("fee.history")}</h2>
    <table>
        <thead>
            <tr>
                <th scope="col">${copy.text("fee.year")}</th>
                <th scope="col">${copy.text("fee.status")}</th>
            </tr>
        </thead>
        <tbody>
            @for(FeeStatus fee : file.fees())
                !{Instant feePaidAt = fee.paidAt();}
                <tr>
                    <td>${String.valueOf(fee.year())}</td>
                    <td>
                        @if(feePaidAt != null)
                            ${copy.text("fee.paid", copy.date(feePaidAt))}
                            @if(fee.throughHousehold())
                                ${copy.text("fee.throughHousehold")}
                            @endif
                        @else
                            ${copy.text("fee.notPaid")}
                        @endif
                    </td>
                </tr>
            @endfor
        </tbody>
    </table>

    <h2>${copy.text("member.household")}</h2>
    @if(household == null)
        <p>${copy.text("member.noHousehold")}</p>
    @else
        <p>${household.name()}</p>
        <ul>
            @for(HouseholdMember other : household.members())
                <li>
                    <a href="/admin/medlemmar/${other.id()}">${other.fullName()}</a>
                    @if(other.hasAccount())
                        ${copy.text("household.hasAccount")}
                    @endif
                </li>
            @endfor
        </ul>
    @endif

    @if(file.member().email() == null)
        <h2>${copy.text("invitation.heading")}</h2>
        @if(invitationExpires != null)
            <p>${copy.text("household.invited", copy.dateTime(invitationExpires))}</p>
        @endif
        <form method="post" action="/admin/medlemmar/${id}/inbjudan">
            @template.tag.csrf(csrf = csrf)
            <label for="invite-email">${copy.text("invitation.email", file.member().fullName())}</label>
            <input id="invite-email" name="email" type="email" autocomplete="off" required>
            <button type="submit">${copy.text("invitation.send")}</button>
        </form>
    @endif

    <h2>${copy.text("register.edit")}</h2>
    @template.tag.errors(copy = copy, errors = errors)
    <form method="post" action="/admin/medlemmar/${id}">
        @template.tag.csrf(csrf = csrf)
        @template.tag.memberFields(copy = copy, form = form, errors = errors, households = households)
        <button type="submit">${copy.text("register.edit.submit")}</button>
    </form>

    <p><a href="/admin/medlemmar/${id}/ta-bort">${copy.text("register.delete")}</a></p>
`)
