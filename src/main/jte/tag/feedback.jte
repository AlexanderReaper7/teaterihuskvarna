@import se.teaterihuskvarna.web.Copy
@import se.teaterihuskvarna.web.Feedback
@param Copy copy
@param Feedback feedback
@if(feedback.stale())
    <p role="alert">${copy.text("form.stale")}</p>
@endif
@if(feedback.error() != null)
    <p role="alert">${feedback.error()}</p>
@endif
@if(feedback.notice() != null)
    <p role="status">${feedback.notice()}</p>
@endif
