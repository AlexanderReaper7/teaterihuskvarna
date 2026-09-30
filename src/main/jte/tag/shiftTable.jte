@import java.util.List
@import se.teaterihuskvarna.volunteer.ShiftDetails
@import se.teaterihuskvarna.web.Copy
@param Copy copy
@param String caption
@param List<ShiftDetails> shifts
<table>
    <caption>${caption}</caption>
    <thead>
        <tr>
            <th scope="col">${copy.text("shifts.event")}</th>
            <th scope="col">${copy.text("shifts.task")}</th>
            <th scope="col">${copy.text("shifts.time")}</th>
            <th scope="col">${copy.text("adminShifts.booked")}</th>
        </tr>
    </thead>
    <tbody>
        @for(ShiftDetails shift : shifts)
            <tr>
                <th scope="row"><a href="/admin/volontarpass/${shift.id()}">${shift.eventTitle()}</a></th>
                <td>${copy.text("shift.task." + shift.task().name())}</td>
                <td>${copy.text("shifts.between", copy.dateTime(shift.startsAt()), copy.time(shift.endsAt()))}</td>
                <td>${copy.text("adminShifts.ofPlaces", shift.booked(), shift.places())}</td>
            </tr>
        @endfor
    </tbody>
</table>
