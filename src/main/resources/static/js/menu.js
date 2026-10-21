// The header's menu button on a narrow screen. Without this script the button
// stays hidden and the navigation stays open, so every link is still reachable.
document.addEventListener("DOMContentLoaded", () => {
    const button = document.querySelector(".menu-button-toggle");
    const nav = document.getElementById("huvudmeny");
    if (!button || !nav) {
        return;
    }
    const narrow = window.matchMedia("(max-width: 767px)");
    const update = () => {
        const  expanded= button.getAttribute("aria-expanded") === "true";
        button.setAttribute("aria-label", expanded ? button.dataset.labelClose: button.dataset.labelOpen);
        button.hidden = !narrow.matches;
        nav.hidden = narrow.matches && button.getAttribute("aria-expanded") !== "true";
    };
    button.addEventListener("click", () => {
        button.setAttribute("aria-expanded", String(button.getAttribute("aria-expanded") !== "true"));
        update();
    });
    document.querySelector(".site-header").addEventListener("keydown", (event) => {
        if (event.key === "Escape" && narrow.matches && button.getAttribute("aria-expanded") === "true") {
            button.setAttribute("aria-expanded", "false");
            update();
            button.focus();
        }
    });
    nav.addEventListener("click", (event) => {
        if (event.target.closest("a") && narrow.matches) {
            button.setAttribute("aria-expanded", "false");
            update();
        }
    });
    narrow.addEventListener("change", () => {
        button.setAttribute("aria-expanded", "false");
        update();
    });
    update();
});
