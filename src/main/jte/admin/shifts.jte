@import java.util.List
@import java.util.Objects
@import se.teaterihuskvarna.content.Event
@import se.teaterihuskvarna.volunteer.ShiftDetails
@import se.teaterihuskvarna.volunteer.ShiftForm
@import se.teaterihuskvarna.volunteer.Task
@import se.teaterihuskvarna.web.Copy
@import se.teaterihuskvarna.web.CsrfField
@import se.teaterihuskvarna.web.FieldErrors
@param Copy copy
@param CsrfField csrf
@param List<ShiftDetails> shifts
@param List<ShiftDetails> past
@param List<Event> events
@param ShiftForm form
@param FieldErrors errors
@param String error
@param String notice
!{String eventError = errors.message("eventId");}
!{String taskError = errors.message("task");}
!{String timesError = errors.message("endsAfterStart");}
@template.layout(copy = copy, title = copy.text("adminShifts.heading"), content = @`
    <h1>${copy.text("adminShifts.heading")}</h1>
    <p><a href="/admin">${copy.text("admin.toOverview")}</a></p>
    @if(error != null)
        <p role="alert">${error}</p>
    @endif
    @if(notice != null)
        <p role="status">${notice}</p>
    @endif
    @if(shifts.isEmpty())
        <p>${copy.text("adminShifts.none")}</p>
    @else
        @template.tag.shiftTable(copy = copy, caption = copy.text("adminShifts.caption"), shifts = shifts)
    @endif

    <h2>${copy.text("adminShifts.new")}</h2>
    @if(events.isEmpty())
        <p>${copy.text("adminShifts.noEvents")}</p>
    @else
        @template.tag.errors(copy = copy, errors = errors)
        <form method="post" action="/admin/volontarpass">
            @template.tag.csrf(csrf = csrf)
            <p>
                <label for="eventId">${copy.text("adminShifts.form.event")}</label>
                <select id="eventId" name="eventId" required aria-invalid="${eventError == null ? null : "true"}"
                        aria-describedby="${eventError == null ? null : "eventId-error"}">
                    <option value="">${copy.text("adminShifts.form.chooseEvent")}</option>
                    @for(Event event : events)
                        <option value="${event.id()}" selected="${event.id().equals(form.eventId())}">${event.title()}, ${copy.dateTime(event.startsAt())}</option>
                    @endfor
                </select>
                @if(eventError != null)
                    <span id="eventId-error" class="field-error">${eventError}</span>
                @endif
            </p>
            <fieldset>
                <legend>${copy.text("adminShifts.form.task")}</legend>
                @for(Task task : Task.values())
                    <label><input id="${task.ordinal() == 0 ? "task" : null}" type="radio" name="task" value="${task.name()}" required checked="${task == form.task()}"> ${copy.text("shift.task." + task.name())}</label>
                @endfor
                @if(taskError != null)
                    <span class="field-error">${taskError}</span>
                @endif
            </fieldset>
            @template.tag.field(name = "startsAt", label = copy.text("adminShifts.form.startsAt"), type = "datetime-local",
                    autocomplete = "off", value = Objects.toString(form.startsAt(), null),
                    error = errors.message("startsAt"), required = true)
            <div id="endsAfterStart">
            @template.tag.field(name = "endsAt", label = copy.text("adminShifts.form.endsAt"), type = "datetime-local",
                    autocomplete = "off", value = Objects.toString(form.endsAt(), null),
                    error = timesError != null ? timesError : errors.message("endsAt"), required = true)
            </div>
            @template.tag.field(name = "places", label = copy.text("adminShifts.form.places"), type = "number",
                    autocomplete = "off", value = Objects.toString(form.places(), null),
                    error = errors.message("places"), required = true)
            <button type="submit">${copy.text("adminShifts.form.submit")}</button>
        </form>
    @endif

    <h2>${copy.text("adminShifts.past")}</h2>
    @if(past.isEmpty())
        <p>${copy.text("adminShifts.noPast")}</p>
    @else
        @template.tag.shiftTable(copy = copy, caption = copy.text("adminShifts.pastCaption"), shifts = past)
    @endif
`)
