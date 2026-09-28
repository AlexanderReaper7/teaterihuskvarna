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
  button.addEventListener("click", () => {
    button.setAttribute("aria-expanded", String(button.getAttribute("aria-expanded") !== "true"));
    update();
  });
  narrow.addEventListener("change", update);
  update();
});
