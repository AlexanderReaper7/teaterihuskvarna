// The administrators' walkthrough, "rundturen", on every administrator page.
// Without this script the start button stays hidden and nothing else changes.
//
// tag/adminTour.jte writes the steps and the card into a <template>, with every
// Swedish text, so nothing is written here. A step says which page it is on
// (data-path, data-search, data-on), what it points at (data-target) and where
// a link to it leads (data-href). This script puts the card into the page just
// above the block that holds the target, and outlines the target. The card is
// part of the page and not a layer over it, so it covers nothing, reads in
// order for a screen reader, and the next Tab press lands on its buttons and
// then on what it describes.
//
// The steps are in a page only while a walkthrough runs. Starting one sets the
// cookie se.teaterihuskvarna.web.Walkthrough reads, and ending it removes the
// cookie, so a page looked at without a walkthrough holds none of its text.
//
// The administrator does each step for real: searches, adds, saves, removes.
// Most of that loads another page, so where the walkthrough stands is kept in
// sessionStorage, which lasts as long as the tab and is not shared with other
// tabs. On each load locate() works out which step the new page shows:
//
// - the next step, when the administrator pressed something inside the target
//   and the page that came back has no error, or when only the next step is
//   on this page;
// - the same step, when it is still on this page;
// - an earlier step of the same errand, when the administrator went back, as
//   from the mailing preview to its form;
// - otherwise none, and the card says the step is on another page and offers
//   a link to it.
//
// Nästa and Föregående skip a step that is on a page only an action leads to,
// such as the page of a member that was never added.
"use strict";

(() => {
  const main = document.getElementById("innehall");
  if (!main) {
    return;
  }
  const KEY = "rundtur";
  // Null in a page sent without the cookie.
  const source = document.getElementById("tour");
  const steps = source ? [...source.content.querySelectorAll("[data-tour-step]")] : [];

  let card = null;
  let target = null;
  // The step the card shows, or -1 when the walkthrough is not running.
  let shown = -1;
  // Whether the last press was inside the target. Kept at pagehide, so a
  // press that loads no page, as on a download link, is forgotten by the
  // next press somewhere else.
  let acted = false;

  // The cookie only says that the next page should hold the steps. It is a
  // session cookie for the administrator pages, and holds nothing.
  const ask = () => {
    document.cookie = `${KEY}=1; Path=/admin; SameSite=Lax${location.protocol === "https:" ? "; Secure" : ""}`;
  };
  const stopAsking = () => {
    document.cookie = `${KEY}=; Path=/admin; Max-Age=0; SameSite=Lax`;
  };

  // A browser may refuse storage, in a private window or by a setting. The
  // walkthrough then works on one page and ends when another loads.
  const read = () => {
    try {
      const state = JSON.parse(sessionStorage.getItem(KEY));
      return state && Number.isInteger(state.step) && state.step >= 0 ? state : null;
    } catch {
      return null;
    }
  };
  const write = (step, pressed) => {
    try {
      sessionStorage.setItem(KEY, JSON.stringify({ step, acted: pressed }));
    } catch {
      // Nothing to do: see read().
    }
  };
  const forget = () => {
    try {
      sessionStorage.removeItem(KEY);
    } catch {
      // Nothing to do: see read().
    }
  };

  // A browser from before :has() (Firefox 120, Chrome 104) reads a selector
  // that uses it as an error. The step then points at nothing, and its card
  // goes to the top of the page.
  const find = (selector) => {
    try {
      return document.querySelector(selector);
    } catch {
      return null;
    }
  };

  const here = (index) => {
    const step = steps[index];
    if (!step) {
      return false;
    }
    const { path, search, on } = step.dataset;
    return (!path || new RegExp(path).test(location.pathname))
        && (!search || location.search.includes(search))
        && (!on || find(on) !== null);
  };
  // Whether the page shows an error. An alert that waits hidden, as the
  // passkey offer's does, has no box. The countdown before a login ends is
  // no answer to what the administrator did.
  const refused = () => [...document.querySelectorAll('[role="alert"], .field-error')]
      .some((element) => element.getClientRects().length > 0 && !element.closest(".login-countdown"));

  const locate = (state) => {
    const at = state.step;
    if (state.acted && here(at + 1) && !refused()) {
      return at + 1;
    }
    if (here(at)) {
      return at;
    }
    if (here(at + 1)) {
      return at + 1;
    }
    // Back through the steps that only an action leads to, as far as the
    // step whose link starts the errand.
    for (let i = at; i > 0 && !steps[i].dataset.href; i--) {
      if (here(i - 1)) {
        return i - 1;
      }
    }
    return -1;
  };

  const clear = () => {
    card?.remove();
    target?.classList.remove("tour-target");
    card = null;
    target = null;
    shown = -1;
    acted = false;
  };

  // The child of <main> that holds the element, since the card goes between
  // the page's blocks and never inside a table, a list or a line of links.
  const block = (element) => {
    let at = element;
    while (at && at.parentElement !== main) {
      at = at.parentElement;
    }
    return at;
  };

  const fill = (index, elsewhere) => {
    const step = steps[index];
    const made = source.content.querySelector("[data-tour-card]").cloneNode(true);
    const part = (name) => made.querySelector(`[data-tour-${name}]`);
    part("progress").textContent = source.dataset.progress
        .replace("{0}", String(index + 1)).replace("{1}", String(steps.length));
    part("title").textContent = step.dataset.title;
    // What a step asks for cannot be done on another page, so there the card
    // says only where the step is.
    if (!elsewhere) {
      part("body").append(...[...step.children].map((child) => child.cloneNode(true)));
      part("requirement").textContent = step.dataset.requirement ?? "";
    }
    part("requirement").hidden = elsewhere || !step.dataset.requirement;
    part("elsewhere").hidden = !elsewhere;
    part("resume").hidden = !elsewhere;
    part("next").hidden = elsewhere;
    part("back").hidden = elsewhere || index === 0;
    if (index === steps.length - 1) {
      part("next").querySelector("[data-tour-next-text]").textContent = source.dataset.done;
      part("end").hidden = true;
    }
    return made;
  };

  const top = () => main.querySelector(":scope > h1") ?? main.firstElementChild;

  // Shows a step that is on this page.
  const show = (index) => {
    clear();
    shown = index;
    write(index, false);
    const step = steps[index];
    card = fill(index, false);
    target = step.dataset.target ? find(step.dataset.target) : null;
    const before = block(target);
    if (before) {
      target.classList.add("tour-target");
      before.before(card);
    } else {
      target = null;
      top().after(card);
    }
    // Left where it is when it is already in the upper part of the window,
    // so the page does not jump for a card that can be read as it stands.
    const from = card.getBoundingClientRect().top;
    if (from < 0 || from > window.innerHeight * 0.4) {
      card.scrollIntoView({ block: "start" });
    }
    // A form that was refused has put its own message first, and that is
    // what the administrator has to read.
    if (!refused()) {
      card.focus({ preventScroll: true });
    }
  };

  // Says that the step is on another page.
  const showElsewhere = (index) => {
    clear();
    shown = index;
    card = fill(index, true);
    top().after(card);
  };

  const end = () => {
    clear();
    forget();
    stopAsking();
    document.querySelector("[data-tour-start] button")?.focus();
  };

  // Follows a step's link. The press that asked for it was on the card, so
  // an earlier press inside the target no longer counts.
  const leave = (index) => {
    acted = false;
    write(index, false);
    location.assign(steps[index].dataset.href);
  };

  // To the nearest step in a direction that is on this page or has a link.
  const go = (direction) => {
    for (let i = shown + direction; i >= 0; i += direction) {
      if (i >= steps.length) {
        end();
        return;
      }
      if (here(i)) {
        show(i);
        return;
      }
      if (steps[i].dataset.href) {
        leave(i);
        return;
      }
    }
  };

  // Back to the step, or to the one before it that has a link.
  const resume = () => {
    for (let i = shown; i >= 0; i--) {
      if (steps[i].dataset.href) {
        leave(i);
        return;
      }
    }
  };

  // From the first step. A page sent before the cookie was set holds no
  // steps, and is loaded again to get them.
  const start = (box) => {
    ask();
    write(0, false);
    if (here(0)) {
      show(0);
    } else {
      location.assign(box.dataset.tourStart);
    }
  };

  const begin = () => {
    const state = read();
    if (!state) {
      clear();
      return;
    }
    if (!steps[state.step]) {
      // The cookie is gone while the walkthrough is not over, so this page
      // came without the steps. The next one has them.
      ask();
      return;
    }
    const index = locate(state);
    if (index < 0) {
      showElsewhere(state.step);
    } else {
      show(index);
    }
  };

  document.addEventListener("click", (event) => {
    if (!(event.target instanceof Element)) {
      return;
    }
    const box = event.target.closest("[data-tour-start]");
    if (box && event.target.closest("button")) {
      start(box);
    } else if (card?.contains(event.target)) {
      const pressed = (name) => event.target.closest(`[data-tour-${name}]`);
      if (pressed("next")) {
        go(1);
      } else if (pressed("back")) {
        go(-1);
      } else if (pressed("resume")) {
        resume();
      } else if (pressed("end")) {
        end();
      }
    } else {
      acted = target !== null && target.contains(event.target);
    }
  });
  // A form sent with Enter is no click.
  document.addEventListener("submit", (event) => {
    if (target?.contains(event.target)) {
      acted = true;
    }
  });
  window.addEventListener("pagehide", () => {
    const state = read();
    if (target && acted && state) {
      write(state.step, true);
    }
  });
  // A page the back button restores from the browser's cache still shows the
  // step it showed when it was left.
  window.addEventListener("pageshow", (event) => {
    if (event.persisted) {
      begin();
    }
  });

  const ready = () => {
    for (const box of document.querySelectorAll("[data-tour-start]")) {
      box.hidden = false;
    }
    begin();
  };
  // After tables.js has put each table in its box, which this script would
  // otherwise take for the block that holds a target.
  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", ready);
  } else {
    ready();
  }
})();
