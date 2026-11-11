package se.teaterihuskvarna.login;

/// What opening a login link found, from [LoginLinks#open].
public enum LinkOpening {

    /// The link works in this browser: show the button that logs in.
    HERE,

    /// The link works, but only in the browser that asked for it: say so, and
    /// point to the code.
    ELSEWHERE,

    /// The link works, but only in the browser that sent the membership
    /// application it was made for, and it has no code, because the welcome
    /// page showed it and no mail carried it: send the person to the login page.
    ELSEWHERE_WITHOUT_CODE,

    /// The link is unknown, used, expired or another kind's.
    UNUSABLE
}
