// Sends each form once. Firefox turns a second press, made while the first
// answer is on its way, into a second POST, and the browser shows the answer
// to the second. For the confirmation link that answer is "the link does not
// work", because the first press already used it; for the remove button it was
// a second remove. The layout loads this before any page script, so it also
// sees the submit login-link.js makes.
//
// A page the back button restores from the browser's cache keeps the mark, so
// it is cleared there, or the form would never send again.
"use strict";

document.addEventListener("submit", (event) => {
  const form = event.target;
  if (event.defaultPrevented) {
    return;
  }
  if (form.dataset.sent !== undefined) {
    event.preventDefault();
    return;
  }
  form.dataset.sent = "";
});

window.addEventListener("pageshow", (event) => {
  if (event.persisted) {
    for (const form of document.querySelectorAll("form[data-sent]")) {
      delete form.dataset.sent;
    }
  }
});
