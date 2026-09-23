package se.teaterihuskvarna.login;

/// What opening a login link found, from [LoginLinks#open].
public enum LinkOpening {

    /// The link works in this browser: show the button that logs in.
    HERE,

    /// The link works, but only in the browser that asked for it: say so, and
    /// point to the code.
    ELSEWHERE,

    /// The link is unknown, used, expired or another kind's.
    UNUSABLE
}
