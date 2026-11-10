@import se.teaterihuskvarna.web.Copy
@import se.teaterihuskvarna.web.DeviceUrls
@import se.teaterihuskvarna.web.LoginCountdown
@param Copy copy
<%-- Asked for here and not passed in, so a page cannot leave it out: se.teaterihuskvarna.web.LoginCountdown.
     Null where nobody is logged in, and then there is nothing to count down. --%>
!{LoginCountdown countdown = LoginCountdown.current();}
@if(countdown != null)
!{DeviceUrls urls = countdown.urls();}
<div class="login-countdown" data-login-countdown data-seconds-left="${countdown.status().secondsLeft()}"
        data-status="${urls.status()}" data-extend="${urls.extend()}" data-csrf="${countdown.csrf()}"
        data-ending="${copy.text("session.ending")}" data-remaining="${copy.text("session.remaining")}"
        data-ended="${copy.text("session.ended")}">
    <div data-login-ending hidden>
        <p role="alert" data-login-ending-text></p>
        <p data-login-remaining></p>
        <button type="button" data-login-extend>@template.icon.refreshCw() ${copy.text("session.extend")}</button>
    </div>
    <div data-login-ended hidden>
        <p role="alert" data-login-ended-text></p>
        <a href="${urls.login()}">@template.icon.logIn() ${copy.text("session.login")}</a>
    </div>
</div>
<script src="/js/session.js" defer></script>
@endif
