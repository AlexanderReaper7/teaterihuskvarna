// A row of links, such as the footer's or the register's, keeps each part
// apart from the next on one line, and never ends a line with a separator when
// it wraps.
import { expect, test, type Page } from "@playwright/test";
import { linkLogin } from "../support/auth";
import { clearLinkRequests, resetAdministrators } from "../support/db";
import { ADMINISTRATORS } from "../support/site";

test.beforeEach(async () => {
  await resetAdministrators();
  await clearLinkRequests();
});

/// What is wrong with the row, the paragraph that holds `selector`, as
/// readable sentences.
async function problems(page: Page, selector: string): Promise<string[]> {
  return page.locator(selector).first().evaluate((inside) => {
    const row = inside.closest("p")!;
    const parts = [...row.childNodes]
      .filter((n) => n.nodeType === Node.ELEMENT_NODE || n.textContent!.trim())
      .map((node) => {
        const range = document.createRange();
        range.selectNodeContents(node);
        const lines = [...range.getClientRects()].filter((r) => r.width > 0.5);
        return { words: node.textContent!.trim().replace(/\s+/g, " "), first: lines[0], last: lines[lines.length - 1] };
      });
    const found: string[] = [];
    for (let i = 1; i < parts.length; i++) {
      const [a, b] = [parts[i - 1], parts[i]];
      if (a.last.top < b.first.bottom && b.first.top < a.last.bottom) {
        const gap = b.first.left - a.last.right;
        if (gap < 16) {
          found.push(`${JSON.stringify(a.words)} and ${JSON.stringify(b.words)} are ${gap.toFixed(0)} px apart`);
        }
      } else if (/[·|•–-]$/.test(a.words)) {
        found.push(`a line ends with the separator in ${JSON.stringify(a.words)}`);
      }
    }
    return found;
  }, selector);
}

for (const width of [320, 390, 900, 2560]) {
  test.describe(`${width} px wide`, () => {
    test.use({ viewport: { width, height: 900 } });

    test("the footer keeps the association's name apart from the administration link", async ({ page }) => {
      await page.goto("/");
      expect(await problems(page, '.site-footer a[href="/admin"]')).toEqual([]);
    });

    test("the register's links stand apart", async ({ page }) => {
      await linkLogin(page, "administrator", ADMINISTRATORS.ada);
      await page.goto("/admin/medlemmar");
      expect(await problems(page, 'main a[href="/admin/medlemmar/ny"]')).toEqual([]);
    });
  });
}
