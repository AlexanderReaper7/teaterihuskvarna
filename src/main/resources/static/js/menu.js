// The header's menu button on a narrow screen. Without this script the button
// stays hidden and the navigation stays open, so every link is still reachable.
document.addEventListener("DOMContentLoaded", () => {
  const button = document.querySelector(".menu-button");
  const nav = document.getElementById("huvudmeny");
  if (!button || !nav) {
    return;
  }
  const narrow = window.matchMedia("(max-width: 767px)");
  const update = () => {
    button.hidden = !narrow.matches;
    nav.hidden = narrow.matches && button.getAttribute("aria-expanded") !== "true";
  };
  const close = () => {
    button.setAttribute("aria-expanded", "false");
    update();
  };
  button.addEventListener("click", () => {
    button.setAttribute("aria-expanded", String(button.getAttribute("aria-expanded") !== "true"));
    update();
  });
  // Escape closes an open menu and returns focus to the button, so a keyboard
  // user is not left on a link that just disappeared.
  document.querySelector(".site-header").addEventListener("keydown", (event) => {
    if (event.key === "Escape" && narrow.matches && button.getAttribute("aria-expanded") === "true") {
      close();
      button.focus();
    }
  });
  nav.addEventListener("click", (event) => {
    if (event.target.closest("a") && narrow.matches) {
      close();
    }
  });
  narrow.addEventListener("change", close);
  update();
});
