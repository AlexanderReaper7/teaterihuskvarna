@import se.teaterihuskvarna.login.LoginStatus
@import se.teaterihuskvarna.web.Copy
@import se.teaterihuskvarna.web.DeviceUrls
@param Copy copy
@param LoginStatus status
@param DeviceUrls urls
<div class="login-countdown" data-login-countdown data-seconds-left="${status.secondsLeft()}"
        data-status="${urls.status()}" data-extend="${urls.extend()}"
        data-ending="${copy.text("session.ending")}" data-remaining="${copy.text("session.remaining")}"
        data-ended="${copy.text("session.ended")}">
    <div data-login-ending hidden>
        <p role="alert" data-login-ending-text></p>
        <p data-login-remaining></p>
        <button type="button" data-login-extend>${copy.text("session.extend")}</button>
    </div>
    <div data-login-ended hidden>
        <p role="alert" data-login-ended-text></p>
        <a href="${urls.login()}">${copy.text("session.login")}</a>
    </div>
</div>
<script src="/js/session.js" defer></script>
