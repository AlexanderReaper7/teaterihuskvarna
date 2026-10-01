@import se.teaterihuskvarna.member.HouseholdDetails
@import se.teaterihuskvarna.member.HouseholdMember
@import se.teaterihuskvarna.member.NewHousehold
@import se.teaterihuskvarna.web.Copy
@import se.teaterihuskvarna.web.CsrfField
@import se.teaterihuskvarna.web.FieldErrors
@param Copy copy
@param CsrfField csrf
@param HouseholdDetails household = null
@param Long ownMemberId
@param NewHousehold form
@param FieldErrors errors
@param boolean stale
@param String error
@param String notice
@template.member.layout(copy = copy, title = copy.text("member.household.manage"), content = @`
    <h1>${copy.text(household == null ? "household.create" : "member.household.manage")}</h1>
    @if(stale)
        <p role="alert">${copy.text("form.stale")}</p>
    @endif
    @if(error != null)
        <p role="alert">${error}</p>
    @endif
    @if(notice != null)
        <p role="status">${notice}</p>
    @endif
    @template.tag.errors(copy = copy, errors = errors)
    @if(household == null || household.ownedBy(ownMemberId))
    <form method="post" action="${household == null ? "/medlem/hushall" : "/medlem/hushall/namn"}">
        @template.tag.csrf(csrf = csrf)
        @template.tag.field(name = "name", label = copy.text("household.name"), type = "text",
                autocomplete = "off", value = form.name(), error = errors.message("name"), required = true)
        <button type="submit">${copy.text(household == null ? "household.create.submit" : "member.household.rename")}</button>
    </form>
    @else
        <p>${household.name()}</p>
    @endif
    @if(household != null)
        <h2>${copy.text("member.household.members")}</h2>
        <p>${copy.text("member.household.permissions")}</p>
        <ul>
            @for(HouseholdMember member : household.members())
                <li>
                    ${member.fullName()}
                    @if(member.hasAccount())
                        ${copy.text("household.hasAccount")}
                    @endif
                    @if(household.ownedBy(ownMemberId))
                        <a href="/medlem/hushall/medlemmar/${member.id()}">${copy.text("member.household.edit", member.fullName())}</a>
                    @endif
                    @if(household.ownedBy(ownMemberId) || member.id().equals(ownMemberId))
                    <a href="/medlem/hushall/medlemmar/${member.id()}/ta-bort">${copy.text(member.id().equals(ownMemberId)
                            ? "member.household.leave" : "member.household.remove", member.fullName())}</a>
                    @endif
                </li>
            @endfor
        </ul>
        @if(household.ownedBy(ownMemberId))
            <p><a href="/medlem/hushall/ny">${copy.text("member.household.add")}</a></p>
        @endif
        <p>${copy.text("member.household.invitationNote")}</p>
    @else
        <p>${copy.text("member.household.createNote")}</p>
    @endif
    <p><a href="/medlem">${copy.text("member.back")}</a></p>
`)
