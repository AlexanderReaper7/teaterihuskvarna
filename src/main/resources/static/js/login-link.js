// Posts the form on the page a mailed login link opens, so a person who follows
// the link is logged in without pressing the button. The server itself never
// logs in on GET, because mail scanners fetch links: docs/decisions/0015.
// Without JavaScript the button still works.
document.querySelector("form[data-auto-submit]")?.requestSubmit();
