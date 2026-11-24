@import java.util.List
@import se.teaterihuskvarna.volunteer.ShiftDetails
@import se.teaterihuskvarna.volunteer.Volunteer
@import se.teaterihuskvarna.web.Copy
@import se.teaterihuskvarna.web.Feedback
@param Copy copy
@param ShiftDetails shift
@param List<Volunteer> volunteers
@param Feedback feedback
!{String title = copy.text("adminShifts.shiftTitle", copy.text("shift.task." + shift.task().name()), shift.eventTitle());}
@template.admin.layout(copy = copy, title = title, content = @`
    <h1>${title}</h1>
    <p><a href="/admin/volontarpass">${copy.text("adminShifts.back")}</a></p>
    @template.tag.feedback(copy = copy, feedback = feedback)
    <dl>
        <dt>${copy.text("shifts.time")}</dt>
        <dd>${copy.text("shifts.between", copy.dateTime(shift.startsAt()), copy.time(shift.endsAt()))}</dd>
        <dt>${copy.text("adminShifts.booked")}</dt>
        <dd>${copy.text("adminShifts.ofPlaces", shift.booked(), shift.places())}</dd>
    </dl>

    <h2>${copy.text("adminShifts.volunteers")}</h2>
    @if(volunteers.isEmpty())
        <p>${copy.text("adminShifts.noVolunteers")}</p>
    @else
        <p><a href="/admin/volontarpass/${shift.id()}/bokningar.csv">@template.icon.download() ${copy.text("adminShifts.csv")}</a></p>
        <table>
            <caption>${copy.text("adminShifts.volunteersCaption", title)}</caption>
            <thead>
                <tr>
                    <th scope="col">${copy.text("shift.csv.name")}</th>
                    <th scope="col">${copy.text("member.email")}</th>
                    <th scope="col">${copy.text("shift.csv.phone")}</th>
                    <th scope="col">${copy.text("shift.csv.bookedAt")}</th>
                </tr>
            </thead>
            <tbody>
                @for(Volunteer volunteer : volunteers)
                    !{String email = volunteer.email();}
                    !{String phone = volunteer.phone();}
                    <tr>
                        <th scope="row">${volunteer.fullName()}</th>
                        <td>@if(email != null)${email}@endif</td>
                        <td class="tel">@if(phone != null)${phone}@endif</td>
                        <td>${copy.dateTime(volunteer.bookedAt())}</td>
                    </tr>
                @endfor
            </tbody>
        </table>
    @endif

    <h2>${copy.text("adminShifts.delete")}</h2>
    <p><a href="/admin/volontarpass/${shift.id()}/ta-bort">@template.icon.trash2() ${copy.text("adminShifts.delete")}</a></p>
`)
