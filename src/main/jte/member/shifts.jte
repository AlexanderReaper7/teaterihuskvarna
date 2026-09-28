@import java.util.List
@import se.teaterihuskvarna.volunteer.MemberShift
@import se.teaterihuskvarna.web.Copy
@import se.teaterihuskvarna.web.CsrfField
@param Copy copy
@param CsrfField csrf
@param List<MemberShift> shifts
@param String error
@param String notice
@template.layout(copy = copy, title = copy.text("shifts.heading"), content = @`
    <h1>${copy.text("shifts.heading")}</h1>
    <p><a href="/medlem">${copy.text("member.toOverview")}</a></p>
    <p>${copy.text("shifts.intro")}</p>
    @if(error != null)
        <p role="alert">${error}</p>
    @endif
    @if(notice != null)
        <p role="status">${notice}</p>
    @endif
    @if(shifts.isEmpty())
        <p>${copy.text("shifts.none")}</p>
    @else
        <table>
            <caption>${copy.text("shifts.caption")}</caption>
            <thead>
                <tr>
                    <th scope="col">${copy.text("shifts.event")}</th>
                    <th scope="col">${copy.text("shifts.task")}</th>
                    <th scope="col">${copy.text("shifts.time")}</th>
                    <th scope="col">${copy.text("shifts.placesLeft")}</th>
                    <th scope="col">${copy.text("shifts.action")}</th>
                </tr>
            </thead>
            <tbody>
                @for(MemberShift shift : shifts)
                    <tr>
                        <th scope="row"><a href="/evenemang/${shift.eventSlug()}">${shift.eventTitle()}</a></th>
                        <td>${copy.text("shift.task." + shift.task().name())}</td>
                        <td>${copy.text("shifts.between", copy.dateTime(shift.startsAt()), copy.time(shift.endsAt()))}</td>
                        <td>${shift.placesLeft()}</td>
                        <td>
                            @if(shift.booked())
                                <form method="post" action="/medlem/volontar/${shift.id()}/avboka">
                                    @template.tag.csrf(csrf = csrf)
                                    <button type="submit" class="button-secondary">${copy.text("shifts.cancel")}</button>
                                </form>
                            @elseif(shift.placesLeft() > 0)
                                <form method="post" action="/medlem/volontar/${shift.id()}/boka">
                                    @template.tag.csrf(csrf = csrf)
                                    <button type="submit">${copy.text("shifts.book")}</button>
                                </form>
                            @else
                                ${copy.text("shifts.full")}
                            @endif
                        </td>
                    </tr>
                @endfor
            </tbody>
        </table>
    @endif
`)
