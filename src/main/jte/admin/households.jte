@import java.util.List
@import se.teaterihuskvarna.member.HouseholdDetails
@import se.teaterihuskvarna.member.HouseholdMember
@import se.teaterihuskvarna.member.NewHousehold
@import se.teaterihuskvarna.web.Copy
@import se.teaterihuskvarna.web.CsrfField
@import se.teaterihuskvarna.web.FieldErrors
@param Copy copy
@param CsrfField csrf
@param List<HouseholdDetails> households
@param NewHousehold form
@param FieldErrors errors
@param String notice
@param boolean stale
@template.admin.layout(copy = copy, title = copy.text("household.heading"), content = @`
    <h1>${copy.text("household.heading")}</h1>
    <p><a href="/admin/medlemmar">${copy.text("register.toList")}</a></p>
    @if(stale)
        <p role="alert">${copy.text("form.stale")}</p>
    @endif
    @if(notice != null)
        <p role="status">${notice}</p>
    @endif
    <p>${copy.text("household.intro")}</p>
    @if(households.isEmpty())
        <p>${copy.text("household.none")}</p>
    @endif
    @for(HouseholdDetails household : households)
        <h2>${household.name()}</h2>
        @if(household.members().isEmpty())
            <p>${copy.text("household.empty")}</p>
        @else
            <ul>
                @for(HouseholdMember member : household.members())
                    <li>
                        <a href="/admin/medlemmar/${member.id()}">${member.fullName()}</a>
                        @if(member.hasAccount())
                            ${copy.text("household.hasAccount")}
                        @endif
                    </li>
                @endfor
            </ul>
        @endif
    @endfor

    <h2>${copy.text("household.create")}</h2>
    @template.tag.errors(copy = copy, errors = errors)
    <form method="post" action="/admin/hushall">
        @template.tag.csrf(csrf = csrf)
        @template.tag.field(name = "name", label = copy.text("household.name"), type = "text",
                autocomplete = "off", value = form.name(), error = errors.message("name"), required = true)
        <button type="submit">${copy.text("household.create.submit")}</button>
    </form>
`)
