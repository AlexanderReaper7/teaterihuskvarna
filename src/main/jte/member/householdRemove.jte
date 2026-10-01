@import se.teaterihuskvarna.web.Copy
@import se.teaterihuskvarna.web.CsrfField
@import java.util.List
@import se.teaterihuskvarna.member.HouseholdMember
@param Copy copy
@param CsrfField csrf
@param String fullName
@param long memberId
@param List<HouseholdMember> successors
@param String error = null
@param boolean stale
@template.member.layout(copy = copy, title = copy.text("member.household.removeHeading", fullName), content = @`
    <h1>${copy.text("member.household.removeHeading", fullName)}</h1>
    @if(stale)
        <p role="alert">${copy.text("form.stale")}</p>
    @endif
    @if(error != null)
        <p role="alert">${error}</p>
    @endif
    <p>${copy.text("member.household.removeNote")}</p>
    <p>${copy.text("member.household.removeFeeNote")}</p>
    <form method="post" action="/medlem/hushall/medlemmar/${memberId}/ta-bort">
        @template.tag.csrf(csrf = csrf)
        @if(!successors.isEmpty())
            <label for="successorMemberId">${copy.text("household.successor.label")}</label>
            <select id="successorMemberId" name="successorMemberId" required>
                <option value="">${copy.text("household.successor.choose")}</option>
                @for(HouseholdMember successor : successors)
                    <option value="${successor.id()}">${successor.fullName()}</option>
                @endfor
            </select>
        @endif
        <button type="submit">${copy.text("member.household.removeSubmit")}</button>
    </form>
    <p><a href="/medlem/hushall">${copy.text("member.household.back")}</a></p>
`)
