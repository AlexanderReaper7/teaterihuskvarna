@import se.teaterihuskvarna.web.CsrfField
@param CsrfField csrf
<input type="hidden" name="${csrf.name()}" value="${csrf.value()}">
