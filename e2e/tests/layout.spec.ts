// Pages at a phone's width up to the user's own 2560 px view. Checks what a
// person would see as broken: sideways scrolling, buttons touching each other,
// buttons poking out of the box they sit in, and words in a table broken in two.
import { expect, test, type Page } from "@playwright/test";
import { linkLogin } from "../support/auth";
import { clearLinkRequests, resetAdministrators } from "../support/db";
import { ADMINISTRATORS, MEMBERS, PATHS } from "../support/site";

test.beforeEach(async () => {
  await resetAdministrators();
  await clearLinkRequests();
});

const WIDTHS = [320, 420, 900, 2560];

/// Every problem found on the page as it is now, as readable sentences.
async function problems(page: Page): Promise<string[]> {
  return page.evaluate(() => {
    const found: string[] = [];
    const root = document.documentElement;
    if (root.scrollWidth > root.clientWidth) {
      found.push(`the page scrolls sideways: ${root.scrollWidth} px wide in ${root.clientWidth} px`);
    }
    const visible = [...document.querySelectorAll("button")].filter((b) => b.getClientRects().length > 0);
    const name = (b: Element) => JSON.stringify((b.textContent ?? "").trim().replace(/\s+/g, " "));

    // Buttons side by side or stacked need space between them.
    for (const a of visible) {
      for (const b of visible) {
        if (a === b || a.parentElement !== b.parentElement || !(a.compareDocumentPosition(b) & 4)) {
          continue;
        }
        const ra = a.getBoundingClientRect();
        const rb = b.getBoundingClientRect();
        const sameRow = ra.top < rb.bottom && rb.top < ra.bottom;
        const gap = sameRow
          ? Math.max(ra.left, rb.left) - Math.min(ra.right, rb.right)
          : Math.max(ra.top, rb.top) - Math.min(ra.bottom, rb.bottom);
        if (gap < 4) {
          found.push(`${name(a)} and ${name(b)} are ${gap.toFixed(1)} px apart`);
        }
      }
    }

    // A button stays inside the padding of every box around it.
    for (const button of visible) {
      const r = button.getBoundingClientRect();
      for (let box = button.parentElement; box && box !== document.body; box = box.parentElement) {
        const style = getComputedStyle(box);
        const inner = box.getBoundingClientRect();
        const left = inner.left + parseFloat(style.borderLeftWidth) + parseFloat(style.paddingLeft);
        const right = inner.right - parseFloat(style.borderRightWidth) - parseFloat(style.paddingRight);
        if (r.left < left - 0.5 || r.right > right + 0.5) {
          const where = box.getAttribute("role") ?? box.className ?? box.tagName;
          found.push(`${name(button)} reaches outside the padding of <${box.tagName.toLowerCase()}> ${where}`
            + ` (${r.left.toFixed(0)}-${r.right.toFixed(0)} in ${left.toFixed(0)}-${right.toFixed(0)})`);
          break;
        }
      }
    }
    // A word in a table breaks only where a line may break anyway, after a
    // hyphen, unless it is wider than the room it has. In a table that room is
    // the box the table sits in, not the cell: a table that squeezes its columns
    // makes every cell too narrow for its words, and that squeeze is the fault.
    // In a stacked table the cell is the whole row wide. Widths are measured on
    // a canvas in the word's font.
    const ruler = document.createElement("canvas").getContext("2d")!;
    for (const cell of document.querySelectorAll("td, th")) {
      const table = cell.closest("table");
      if (cell.getClientRects().length === 0 || !table?.parentElement) {
        continue;
      }
      const around = getComputedStyle(cell).display === "block" ? cell : table.parentElement;
      const box = getComputedStyle(around);
      const room = around.clientWidth - parseFloat(box.paddingLeft) - parseFloat(box.paddingRight);
      const texts = document.createTreeWalker(cell, NodeFilter.SHOW_TEXT);
      for (let node = texts.nextNode(); node; node = texts.nextNode()) {
        const font = getComputedStyle(node.parentElement!);
        ruler.font = `${font.fontStyle} ${font.fontWeight} ${font.fontSize} ${font.fontFamily}`;
        // Firefox adds an empty rectangle where a line ends just before a range.
        const lines = (from: number, to: number) => {
          const range = document.createRange();
          range.setStart(node!, from);
          range.setEnd(node!, to);
          return [...range.getClientRects()].filter((rect) => rect.width > 0.5);
        };
        for (const word of (node.textContent ?? "").matchAll(/\S+/g)) {
          const start = word.index!;
          const text = word[0];
          if (lines(start, start + text.length).length < 2 || ruler.measureText(text).width > room) {
            continue;
          }
          for (let i = 1; i < text.length; i++) {
            const before = lines(start + i - 1, start + i)[0];
            const after = lines(start + i, start + i + 1)[0];
            if (before && after && after.top > before.top + 1 && text[i - 1] !== "-") {
              found.push(`${JSON.stringify(text)} breaks after ${JSON.stringify(text.slice(0, i))}`
                + ` in a table with ${room.toFixed(0)} px of room`);
              break;
            }
          }
        }
      }
    }
    return found;
  });
}

for (const width of WIDTHS) {
  test.describe(`${width} px wide`, () => {
    test.use({ viewport: { width, height: 900 } });

    for (const path of [PATHS.member.login, PATHS.administrator.login, "/bli-medlem", "/finns-inte"]) {
      test(`${path} has no layout problems`, async ({ page }) => {
        await page.goto(path);
        expect(await problems(page)).toEqual([]);
      });
    }

    test("the member page with the passkey offer has no layout problems", async ({ page }) => {
      await linkLogin(page, "member", MEMBERS.erik);
      await expect(page.locator("[data-passkey-offer]")).toBeVisible();
      expect(await problems(page)).toEqual([]);
    });

    test("the administrator page with the passkey offer has no layout problems", async ({ page }) => {
      await linkLogin(page, "administrator", ADMINISTRATORS.ada);
      await expect(page.locator("[data-passkey-offer]")).toBeVisible();
      expect(await problems(page)).toEqual([]);
    });

    test("the member register has no layout problems", async ({ page }) => {
      await linkLogin(page, "administrator", ADMINISTRATORS.ada);
      await page.goto("/admin/medlemmar");
      await expect(page.locator("tbody tr").first()).toBeVisible();
      expect(await problems(page)).toEqual([]);
    });
  });
}
