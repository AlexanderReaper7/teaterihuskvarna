@import se.teaterihuskvarna.web.Copy
@param Copy copy
@param boolean paid
@if(paid)
    <span class="badge badge-paid">${copy.text("fee.badge.paid")}</span>
@else
<span class="badge badge-unpaid">${copy.text("fee.badge.unpaid")}</span>
@endif
