// Tables on every screen width. Without this script a table stays a plain
// table, as site.css draws it.
//
// On a narrow screen site.css stacks a table's rows into blocks, one per row,
// and writes each value after its column's heading. CSS cannot look up the
// heading of a cell, so this copies it into the cell's data-label and marks
// the table stackable. A table whose display CSS changes is no longer a table
// to a screen reader in Safari and Chrome, so the roles the elements had are
// written out.
//
// On a wider screen a table with long values can still be wider than the page.
// Each table sits in a box that scrolls sideways instead of the whole page, and
// while it scrolls the box is a named region a keyboard can reach and scroll.
document.addEventListener("DOMContentLoaded", () => {
  const boxes = [];
  for (const table of document.querySelectorAll("table")) {
    const head = table.tHead;
    if (!head || head.rows.length !== 1) {
      continue;
    }
    const headings = [...head.rows[0].cells].map((cell) => cell.textContent.trim().replace(/\s+/g, " "));
    table.setAttribute("role", "table");
    for (const group of table.querySelectorAll(":scope > thead, :scope > tbody, :scope > tfoot")) {
      group.setAttribute("role", "rowgroup");
    }
    for (const row of table.rows) {
      row.setAttribute("role", "row");
      [...row.cells].forEach((cell, column) => {
        if (row.parentElement === head) {
          cell.setAttribute("role", "columnheader");
          return;
        }
        cell.setAttribute("role", cell.tagName === "TH" ? "rowheader" : "cell");
        // A blank cell would show a heading with nothing after it.
        if (!cell.textContent.trim() && !cell.querySelector("*")) {
          cell.dataset.blank = "";
        }
        // The first cell is the block's title and goes without a heading.
        if (column > 0 && headings[column]) {
          cell.dataset.label = headings[column];
        }
      });
    }
    table.classList.add("stackable");

    const box = document.createElement("div");
    box.className = "table-scroll";
    table.before(box);
    box.append(table);
    boxes.push(box);
  }

  const name = (box) => box.querySelector("caption")?.textContent.trim() || "Tabell";
  const update = () => {
    for (const box of boxes) {
      if (box.scrollWidth > box.clientWidth) {
        box.tabIndex = 0;
        box.setAttribute("role", "region");
        box.setAttribute("aria-label", name(box));
      } else {
        box.removeAttribute("tabindex");
        box.removeAttribute("role");
        box.removeAttribute("aria-label");
      }
    }
  };
  update();
  window.addEventListener("resize", update);
});
