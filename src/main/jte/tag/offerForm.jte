@import se.teaterihuskvarna.offer.OfferForm
@import se.teaterihuskvarna.web.Copy
@import se.teaterihuskvarna.web.CsrfField
@import se.teaterihuskvarna.web.FieldErrors
@param Copy copy
@param CsrfField csrf
@param OfferForm form
@param FieldErrors errors
@param String action
@param String submit
@param gg.jte.Content icon
!{String descriptionError = errors.message("description");}
!{String descriptionInvalid = descriptionError == null ? null : "true";}
!{String descriptionDescribedBy = descriptionError == null ? null : "description-error";}
@template.tag.errors(copy = copy, errors = errors)
<form method="post" action="${action}">
    @template.tag.csrf(csrf = csrf)
    @template.tag.field(copy = copy, name = "title", label = copy.text("adminOffers.form.title"), type = "text",
            autocomplete = "off", value = form.title(), error = errors.message("title"), required = true)
    <p>
        <label for="description">${copy.text("adminOffers.form.description")} ${copy.text("form.optional")}</label>
        <textarea id="description" name="description" rows="8"
                  aria-invalid="${descriptionInvalid}" aria-describedby="${descriptionDescribedBy}">${form.description()}</textarea>
        @if(descriptionError != null)
            <span id="description-error" class="field-error">${descriptionError}</span>
        @endif
    </p>
    @template.tag.field(copy = copy, name = "startsAt", label = copy.text("adminOffers.form.startsAt"), type = "datetime-local",
            autocomplete = "off", value = errors.value("startsAt", form.startsAt()),
            error = errors.message("startsAt"), required = false)
    @template.tag.field(copy = copy, name = "registrationClosesAt", label = copy.text("adminOffers.form.registrationClosesAt"),
            type = "datetime-local", autocomplete = "off",
            value = errors.value("registrationClosesAt", form.registrationClosesAt()),
            error = errors.message("registrationClosesAt"), required = false)
    <p>${copy.text("adminOffers.form.closesHint")}</p>
    @template.tag.field(copy = copy, name = "capacity", label = copy.text("adminOffers.form.capacity"), type = "number",
            autocomplete = "off", value = errors.value("capacity", form.capacity()),
            error = errors.message("capacity"), required = false, max = String.valueOf(OfferForm.CAPACITY_MAX))
    <p>${copy.text("adminOffers.form.capacityHint")}</p>
    <button type="submit">${icon} ${submit}</button>
</form>
