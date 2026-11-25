// The walkthroughs, "rundturen": the administrators' on every administrator
// page and the members' on every page of Mina sidor. Without this script the
// start button stays hidden and nothing else changes. What is said here of
// the administrator holds for the member on the member pages.
//
// tag/tour.jte writes the card into a <template>, and tag/adminTour.jte or
// tag/memberTour.jte the steps, with every Swedish text, so nothing is
// written here. The script's own tag says which section the page belongs to
// (data-root, /admin or /medlem). Each section has its own walkthrough: where
// it stands is kept under the section's name, and its cookie goes only to
// the section's pages, so one started among the member pages shows nothing
// on an administrator page. A step says which page it is on
// (data-path, data-search, data-on), what it points at (data-target) and where
// a link to it leads (data-href). This script outlines the target, or, when
// the target is a heading, frames the heading with everything under it as
// far as the next heading, since such a step is about the whole section. It
// shows the card over the page, fixed to the bottom of the window, so the page under it
// keeps its own layout. The card never lies on the target: it takes the right
// corner, and where that would cover the target it is a bar along the bottom
// edge instead, which the page can scroll everything out from under. In the
// page's order the card stands just before the block that holds the target,
// so a screen reader reads it there, and Tab goes from its buttons to what it
// describes.
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
//   on this page. When that next step is on another page and has a link,
//   begin() follows the link, so a press that did the step always leads on;
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
  // The section's first page, which names its walkthrough.
  const ROOT = document.currentScript?.dataset.root ?? "/admin";
  const STORE = `${KEY}:${ROOT}`;
  // Null in a page sent without the cookie.
  const source = document.getElementById("tour");
  const steps = source ? [...source.content.querySelectorAll("[data-tour-step]")] : [];

  let card = null;
  let target = null;
  // The frame around a section, when the target is a heading.
  let frame = null;
  // The step the card shows, or -1 when the walkthrough is not running.
  let shown = -1;
  // Whether the last press was inside the target. Kept at pagehide, so a
  // press that loads no page, as on a download link, is forgotten by the
  // next press somewhere else.
  let acted = false;

  // The cookie only says that the next page should hold the steps. It is a
  // session cookie for the section's pages, and holds nothing.
  const ask = () => {
    document.cookie = `${KEY}=1; Path=${ROOT}; SameSite=Lax${location.protocol === "https:" ? "; Secure" : ""}`;
  };
  const stopAsking = () => {
    document.cookie = `${KEY}=; Path=${ROOT}; Max-Age=0; SameSite=Lax`;
  };

  // A browser may refuse storage, in a private window or by a setting. The
  // walkthrough then works on one page and ends when another loads.
  const read = () => {
    try {
      const state = JSON.parse(sessionStorage.getItem(STORE));
      return state && Number.isInteger(state.step) && state.step >= 0 ? state : null;
    } catch {
      return null;
    }
  };
  const write = (step, pressed) => {
    try {
      sessionStorage.setItem(STORE, JSON.stringify({ step, acted: pressed }));
    } catch {
      // Nothing to do: see read().
    }
  };
  const forget = () => {
    try {
      sessionStorage.removeItem(STORE);
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
    frame?.remove();
    sizes.disconnect();
    document.documentElement.classList.remove("tour-bar-open");
    target?.classList.remove("tour-target", "tour-section");
    card = null;
    frame = null;
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
    }
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

  // The heading and everything under it, as far as the next heading of its
  // level or a higher one, or a block that holds one, as a <section> with its
  // own heading does. Null when the target is no heading.
  const section = () => {
    const level = /^H([1-6])$/.exec(target.tagName)?.[1];
    if (!level) {
      return null;
    }
    const next = Array.from({ length: Number(level) }, (_, i) => `h${i + 1}`).join(",");
    const parts = [target];
    for (let at = target.nextElementSibling; at && !at.matches(next) && !at.querySelector(next);
      at = at.nextElementSibling) {
      parts.push(at);
    }
    return parts;
  };

  // The box of what the step is about, in the window: the section, or the
  // target, or for a table the box that scrolls it sideways, which carries
  // the outline since it would cut the table's own off.
  const area = () => {
    const parts = section()
        ?? [target.parentElement?.matches(".table-scroll") ? target.parentElement : target];
    const boxes = parts.map((part) => part.getBoundingClientRect())
        .filter((box) => box.width > 0 || box.height > 0);
    if (boxes.length === 0) {
      return target.getBoundingClientRect();
    }
    const left = Math.min(...boxes.map((box) => box.left));
    const top = Math.min(...boxes.map((box) => box.top));
    const right = Math.max(...boxes.map((box) => box.right));
    const bottom = Math.max(...boxes.map((box) => box.bottom));
    return { left, top, right, bottom, width: right - left, height: bottom - top };
  };

  // Whether the card lies on that box or on its outline, which stands 4 px
  // off it and is --focus-width wide.
  const covered = () => {
    if (!target) {
      return false;
    }
    const over = card.getBoundingClientRect();
    const under = area();
    return over.left < under.right + 8 && under.left - 8 < over.right
        && over.top < under.bottom + 8 && under.top - 8 < over.bottom;
  };

  const bar = () => (card.classList.contains("tour-bar") ? card.offsetHeight : 0);

  // Brings that box into the part of the window above the bar, unless its
  // top already shows in the upper half of that part, so the page does not
  // jump for a step that can be seen. A tall box goes to the top, 1.5 rem
  // under the window's edge, and a low one to the middle.
  const reveal = () => {
    const box = area();
    const height = window.innerHeight - bar();
    if (box.top >= 0 && (box.bottom <= height || box.top < height * 0.5)) {
      return;
    }
    window.scrollBy(0, box.height > height * 0.5 ? box.top - 24 : (box.top + box.bottom - height) / 2);
  };

  // What follows a size: the room the page leaves for the bar, and the place
  // of the frame around a section. The bar's text takes one more line when
  // the page's font arrives after the first drawing, and the page under the
  // frame changes with the window, so both are read again on every change.
  const settle = () => {
    if (card?.classList.contains("tour-bar")) {
      document.documentElement.style.setProperty("--tour-bar", `${card.offsetHeight}px`);
    }
    if (frame) {
      const box = area();
      frame.style.left = `${box.left + window.scrollX}px`;
      frame.style.top = `${box.top + window.scrollY}px`;
      frame.style.width = `${box.width}px`;
      frame.style.height = `${box.height}px`;
    }
  };
  const sizes = new ResizeObserver(settle);

  // Where the card goes: the lower right corner of the window, or a bar along
  // the bottom edge when the corner would cover what the step is about, or
  // when the card is more than half as wide as the window and so leaves no
  // corner. The bar hides the window's bottom over its whole width, so
  // site.css gives the page as much room after its end, and for that the
  // page is told how tall the bar is (--tour-bar).
  const place = () => {
    if (!card) {
      return;
    }
    const root = document.documentElement;
    card.classList.remove("tour-bar");
    const corner = card.offsetWidth * 2 <= root.clientWidth && !covered();
    card.classList.toggle("tour-bar", !corner);
    root.classList.toggle("tour-bar-open", !corner);
    settle();
    sizes.observe(card);
    sizes.observe(main);
  };

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
      if (section()) {
        // Outside <main>, whose rules for its blocks would size it, and over
        // the page without taking a press meant for what it frames.
        target.classList.add("tour-section");
        frame = document.createElement("div");
        frame.className = "tour-frame";
        document.body.append(frame);
      }
    } else {
      target = null;
      top().after(card);
    }
    // A form that was refused has put its own message first, and that is
    // what the administrator has to read, so the page stays where it is and
    // focus stays on the message.
    const answered = refused();
    // The card's place depends on where the target is in the window, so the
    // target comes into the window first, and once more if the card became
    // a bar and took the part of the window the target was in.
    if (target && !answered) {
      reveal();
    }
    place();
    if (!answered) {
      if (target) {
        reveal();
      }
      card.focus({ preventScroll: true });
    }
  };

  // Says that the step is on another page.
  const showElsewhere = (index) => {
    clear();
    shown = index;
    card = fill(index, true);
    top().after(card);
    place();
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
    // The step was done and the next one is on another page: go there.
    const after = state.step + 1;
    if (state.acted && !refused() && !here(after) && steps[after]?.dataset.href) {
      leave(after);
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
  // A window that changed size may have room for the corner, or none left.
  window.addEventListener("resize", place);
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
