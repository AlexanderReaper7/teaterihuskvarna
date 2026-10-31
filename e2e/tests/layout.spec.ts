// Pages at a phone's width up to the user's own 2560 px view. Checks what a
// person would see as broken: sideways scrolling, buttons touching each other,
// buttons poking out of the box they sit in, and radio buttons or checkboxes
// away from their text.
import { expect, test, type Page } from "@playwright/test";
import { linkLogin } from "../support/auth";
import { text } from "../support/copy";
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
    // A radio button or checkbox is at least 24 px square, the WCAG 2.2 target
    // size, and no wider than it looks: a stretched one draws its box in the
    // middle of a wide element, so the element's edge says nothing about where
    // the box is. It sits just before the first letter of its label, on the
    // same line.
    for (const input of document.querySelectorAll<HTMLInputElement>("input[type=radio], input[type=checkbox]")) {
      if (input.getClientRects().length === 0) {
        continue;
      }
      const size = input.getBoundingClientRect();
      if (size.width < 23.5 || size.height < 23.5 || size.width > 32) {
        found.push(`a ${input.type} is ${size.width.toFixed(0)} by ${size.height.toFixed(0)} px`);
        continue;
      }
      const label = input.labels?.[0];
      const texts = label ? document.createTreeWalker(label, NodeFilter.SHOW_TEXT) : null;
      let first: Node | null = null;
      for (let node = texts?.nextNode() ?? null; node && !first; node = texts!.nextNode()) {
        first = node.textContent?.trim() ? node : null;
      }
      if (!label || !first) {
        found.push(`a ${input.type} has no label text`);
        continue;
      }
      const letter = document.createRange();
      const at = first.textContent!.search(/\S/);
      letter.setStart(first, at);
      letter.setEnd(first, at + 1);
      const l = letter.getBoundingClientRect();
      const box = input.getBoundingClientRect();
      const gap = l.left - box.right;
      if (l.top >= box.bottom || box.top >= l.bottom || gap < 0 || gap > 24) {
        found.push(`the ${input.type} for ${JSON.stringify(label.textContent!.trim().slice(0, 40))}`
          + ` is not just before its text (${gap.toFixed(0)} px to the right, ${(l.top - box.top).toFixed(0)} px down)`);
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

    for (const path of ["/admin/utskick", "/admin/volontarpass"]) {
      test(`${path} has no layout problems`, async ({ page }) => {
        await linkLogin(page, "administrator", ADMINISTRATORS.ada);
        await page.goto(path);
        await expect(page.locator("input[type=checkbox], input[type=radio]").first()).toBeVisible();
        expect(await problems(page)).toEqual([]);
      });
    }

    test("an unpaid member's page has no layout problems", async ({ page }) => {
      await linkLogin(page, "administrator", ADMINISTRATORS.ada);
      await page.goto("/admin/medlemmar");
      await page.getByRole("row").filter({ hasText: text("fee.notPaid") }).first().getByRole("link").click();
      await expect(page.locator("input[type=radio]").first()).toBeVisible();
      expect(await problems(page)).toEqual([]);
    });
  });
}
