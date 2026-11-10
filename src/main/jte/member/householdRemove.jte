@import se.teaterihuskvarna.web.Copy
@import se.teaterihuskvarna.web.CsrfField
@import java.util.List
@import se.teaterihuskvarna.member.HouseholdMember
@import se.teaterihuskvarna.member.MemberId
@param Copy copy
@param CsrfField csrf
@param String fullName
@param MemberId memberId
@param List<HouseholdMember> successors
@param String error = null
@param boolean stale
@param boolean leaving
!{String heading = leaving ? copy.text("member.household.leaveHeading")
        : copy.text("member.household.removeHeading", fullName);}
@template.member.layout(copy = copy, title = heading, content = @`
    <h1>${heading}</h1>
    @if(stale)
        <p role="alert">${copy.text("form.stale")}</p>
    @endif
    @if(error != null)
        <p role="alert">${error}</p>
    @endif
    <p>${copy.text(leaving ? "member.household.leaveNote" : "member.household.removeNote")}</p>
    <p>${copy.text(leaving ? "member.household.leaveFeeNote" : "member.household.removeFeeNote")}</p>
    <form method="post" action="/medlem/hushall/medlemmar/${memberId.value()}/ta-bort">
        @template.tag.csrf(csrf = csrf)
        @if(!successors.isEmpty())
            <label for="successorMemberId">${copy.text("household.successor.label")}</label>
            <select id="successorMemberId" name="successorMemberId" required>
                <option value="">${copy.text("household.successor.choose")}</option>
                @for(HouseholdMember successor : successors)
                    <option value="${successor.id().value()}">${successor.fullName()}</option>
                @endfor
            </select>
        @endif
        @if(leaving)
            <button type="submit">@template.icon.doorOpen() ${copy.text("member.household.leave")}</button>
        @else
            <button type="submit">@template.icon.trash2() ${copy.text("member.household.removeSubmit")}</button>
        @endif
    </form>
    <p><a href="/medlem/hushall">${copy.text("member.household.back")}</a></p>
`)
