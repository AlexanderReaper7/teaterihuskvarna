@param String name
@param String label
@param String type
@param String autocomplete
@param String value
@param String error
@param boolean required
!{String errorId = name + "-error";}
!{String invalid = error == null ? null : "true";}
<p>
    <label for="${name}">${label}</label>
    <input id="${name}" name="${name}" type="${type}" autocomplete="${autocomplete}" value="${value}"
           required="${required}" aria-invalid="${invalid}" aria-describedby="${error == null ? null : errorId}">
    @if(error != null)
        <span id="${errorId}" class="field-error">${error}</span>
    @endif
</p>
