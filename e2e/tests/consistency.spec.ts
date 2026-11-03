// One thing looks the same wherever it is: a card's title on every list of
// cards, the buttons of a form that only a POST reaches, and the file chooser
// beside the site's own buttons. Found in a visual review: #185.
import { expect, test, type Page } from "@playwright/test";
import { linkLogin } from "../support/auth";
import { text } from "../support/copy";
import { clearLinkRequests, resetAdministrators } from "../support/db";
import { ADMINISTRATORS } from "../support/site";

test.beforeEach(async () => {
  await resetAdministrators();
  await clearLinkRequests();
});

async function titleStyle(page: Page, path: string): Promise<string> {
  await page.goto(path);
  return page.locator(".card :is(h2, h3)").first().evaluate((title) => {
    const style = getComputedStyle(title);
    return `${style.fontSize} ${style.lineHeight} ${style.marginBottom}`;
  });
}

test("a card's title has one size on the start page, the calendar and the news", async ({ page }) => {
  const calendar = await titleStyle(page, "/kalender");
  expect({ start: await titleStyle(page, "/"), news: await titleStyle(page, "/nyheter") })
    .toEqual({ start: calendar, news: calendar });
});

for (const width of [390, 2560]) {
  test(`the mailing preview's buttons stand apart at ${width} px`, async ({ page }) => {
    await page.setViewportSize({ width, height: 900 });
    await linkLogin(page, "administrator", ADMINISTRATORS.ada);
    await page.goto("/admin/utskick");
    await page.locator("#audience").selectOption({ index: 1 });
    await page.locator("#subject").fill("Höstens föreställningar");
    await page.locator('input[name="events"]').first().check();
    await page.getByRole("button", { name: text("adminMailings.form.preview") }).click();
    const boxes = await page.locator("main form button").evaluateAll((buttons) =>
      buttons.map((b) => b.getBoundingClientRect()).map((r) => ({ top: r.top, bottom: r.bottom, left: r.left, right: r.right })));
    expect(boxes).toHaveLength(2);
    const [a, b] = boxes;
    const gap = a.top < b.bottom && b.top < a.bottom ? b.left - a.right : b.top - a.bottom;
    expect(gap).toBeGreaterThanOrEqual(4);
  });
}

test("the file chooser's button is drawn like the site's secondary buttons", async ({ page }) => {
  await linkLogin(page, "administrator", ADMINISTRATORS.ada);
  await page.goto("/admin/handlingar");
  const [chooser, secondary] = await Promise.all([
    page.locator('input[type="file"]').evaluate((input) => {
      const style = getComputedStyle(input, "::file-selector-button");
      return [style.fontFamily, style.fontWeight, style.borderTopColor, style.color];
    }),
    page.evaluate(() => {
      const button = document.createElement("button");
      button.className = "button-secondary";
      document.querySelector("main")!.append(button);
      const style = getComputedStyle(button);
      return [style.fontFamily, style.fontWeight, style.borderTopColor, style.color];
    }),
  ]);
  expect(chooser).toEqual(secondary);
});
