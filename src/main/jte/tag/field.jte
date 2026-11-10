@import se.teaterihuskvarna.web.Copy
@param Copy copy
@param String name
@param String label
@param String type
@param String autocomplete
@param String value
@param String error
@param boolean required
@param String max = null
!{String errorId = name + "-error";}
!{String invalid = error == null ? null : "true";}
<p>
    <%-- A field that may be left empty says so after its name. --%>
    <label for="${name}">${label}@if(!required) ${copy.text("form.optional")}@endif</label>
    <input id="${name}" name="${name}" type="${type}" autocomplete="${autocomplete}" value="${value}"
           required="${required}" max="${max}" aria-invalid="${invalid}" aria-describedby="${error == null ? null : errorId}">
    @if(error != null)
        <span id="${errorId}" class="field-error">${error}</span>
    @endif
</p>
