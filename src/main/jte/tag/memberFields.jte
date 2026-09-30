@import java.util.List
@import se.teaterihuskvarna.member.HouseholdDetails
@import se.teaterihuskvarna.member.MemberForm
@import se.teaterihuskvarna.web.Copy
@import se.teaterihuskvarna.web.FieldErrors
@param Copy copy
@param MemberForm form
@param FieldErrors errors
@param List<HouseholdDetails> households
@template.tag.field(name = "fullName", label = copy.text("member.name"), type = "text",
        autocomplete = "off", value = form.fullName(), error = errors.message("fullName"), required = true)
@template.tag.field(name = "email", label = copy.text("register.form.email"), type = "email",
        autocomplete = "off", value = form.email(), error = errors.message("email"), required = false)
@template.tag.field(name = "phone", label = copy.text("member.phone"), type = "tel",
        autocomplete = "off", value = form.phone(), error = errors.message("phone"), required = false)
@template.tag.field(name = "address", label = copy.text("member.address"), type = "text",
        autocomplete = "off", value = form.address(), error = errors.message("address"), required = false)
@template.tag.field(name = "postalCode", label = copy.text("member.postalCode"), type = "text",
        autocomplete = "off", value = form.postalCode(), error = errors.message("postalCode"), required = false)
@template.tag.field(name = "city", label = copy.text("member.city"), type = "text",
        autocomplete = "off", value = form.city(), error = errors.message("city"), required = false)
<p>
    <label for="householdId">${copy.text("member.household")}</label>
    <select id="householdId" name="householdId">
        <option value="" selected="${form.householdId() == null}">${copy.text("member.noHousehold")}</option>
        @for(HouseholdDetails household : households)
            <option value="${household.id()}" selected="${household.id().equals(form.householdId())}">
                ${household.name()}
            </option>
        @endfor
    </select>
</p>
