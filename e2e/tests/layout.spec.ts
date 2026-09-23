// Pages at a phone's width up to the user's own 2560 px view. Checks what a
// person would see as broken: sideways scrolling, buttons touching each other,
// and buttons poking out of the box they sit in.
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
  });
}
