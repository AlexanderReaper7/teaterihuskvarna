@import se.teaterihuskvarna.volunteer.ShiftDetails
@import se.teaterihuskvarna.web.Copy
@import se.teaterihuskvarna.web.CsrfField
@import se.teaterihuskvarna.web.Feedback
@param Copy copy
@param CsrfField csrf
@param ShiftDetails shift
@param Feedback feedback
!{String title = copy.text("adminShifts.shiftTitle", copy.text("shift.task." + shift.task().name()), shift.eventTitle());}
!{String time = copy.text("shifts.between", copy.dateTime(shift.startsAt()), copy.time(shift.endsAt()));}
@template.admin.layout(copy = copy, title = copy.text("adminShifts.delete"), content = @`
    <h1>${copy.text("adminShifts.delete")}</h1>
    @template.tag.feedback(copy = copy, feedback = feedback)
    <p>${copy.text("adminShifts.delete.question", title, time)}</p>
    <p>${copy.text("adminShifts.deleteWarning", shift.booked())}</p>
    <p>${copy.text("adminShifts.delete.final")}</p>
    <div class="buttons">
        <form method="post" action="/admin/volontarpass/${shift.id()}/ta-bort">
            @template.tag.csrf(csrf = csrf)
            <button type="submit">@template.icon.trash2() ${copy.text("adminShifts.delete.submit")}</button>
        </form>
    </div>
    <p><a href="/admin/volontarpass/${shift.id()}">${copy.text("adminShifts.delete.cancel")}</a></p>
`)
