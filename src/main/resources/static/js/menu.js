// The header's menu button on a narrow screen. Without this script the button
// stays hidden and the navigation stays open, so every link is still reachable.
//
// layout.jte loads this file in <head> without defer, so it runs before the
// body is parsed and the first paint already shows the menu collapsed. Run
// after that paint, as a deferred script is, it collapsed ten links the
// visitor had already seen, and the page moved up by 590 px on a phone.
//
// Until the page is parsed there is no button and no navigation to hide, so
// the class "menu-pending" on <html> stands in for their hidden attributes:
// under it site.css hides the navigation and shows the button on a narrow
// screen. The first update() sets the attributes and removes the class. The
// listeners are on the document, so the button already works while the rest
// of the page loads.
"use strict";

(() => {
  const root = document.documentElement;
  const narrow = window.matchMedia("(max-width: 767px)");
  const menuButton = () => document.querySelector(".menu-button");
  const inside = (event, selector) => event.target instanceof Element && event.target.closest(selector);

  // Shows what the button's aria-expanded says.
  const update = () => {
    const button = menuButton();
    const nav = document.getElementById("huvudmeny");
    if (button && nav) {
      button.hidden = !narrow.matches;
      nav.hidden = narrow.matches && button.getAttribute("aria-expanded") !== "true";
    }
    root.classList.remove("menu-pending");
  };
  const close = () => {
    menuButton()?.setAttribute("aria-expanded", "false");
    update();
  };

  document.addEventListener("click", (event) => {
    const button = inside(event, ".menu-button");
    if (button) {
      button.setAttribute("aria-expanded", String(button.getAttribute("aria-expanded") !== "true"));
      update();
    } else if (narrow.matches && inside(event, "#huvudmeny a")) {
      close();
    }
  });
  // Escape closes an open menu and returns focus to the button, so a keyboard
  // user is not left on a link that just disappeared.
  document.addEventListener("keydown", (event) => {
    const button = menuButton();
    if (event.key === "Escape" && narrow.matches && button?.getAttribute("aria-expanded") === "true"
        && inside(event, ".site-header")) {
      close();
      button.focus();
    }
  });
  narrow.addEventListener("change", close);
  document.addEventListener("DOMContentLoaded", update);
  // Last, so a browser that failed on anything above keeps the open menu.
  root.classList.add("menu-pending");
})();
