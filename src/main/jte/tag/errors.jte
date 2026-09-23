@import se.teaterihuskvarna.web.Copy
@import se.teaterihuskvarna.web.FieldErrors
@import se.teaterihuskvarna.web.FieldErrors.InvalidField
@param Copy copy
@param FieldErrors errors
@if(!errors.isEmpty())
    <div role="alert">
        <p>${copy.text("form.errors")}</p>
        <ul>
            @for(InvalidField invalid : errors.all())
                <li><a href="#${invalid.field()}">${invalid.message()}</a></li>
            @endfor
        </ul>
    </div>
@endif
