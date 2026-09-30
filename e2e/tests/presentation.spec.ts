// The presentation starts at the public home, with developer tools kept at
// their own address. Follow the site's links to prove the completed pages
// connect, including the member and administrator overviews after login.
import { expect, test, type Page } from "@playwright/test";
import { linkLogin } from "../support/auth";
import { ADMINISTRATORS, MEMBERS } from "../support/site";

async function followLinks(page: Page, selector: string, retainMenu = false): Promise<string[]> {
  const source = page.url();
  const paths = await page.locator(selector).evaluateAll((links) =>
    [...new Set(links.map((link) => link.getAttribute("href") ?? ""))]
      .filter((path) => path.startsWith("/") && !path.startsWith("//")));
  expect(paths.length).toBeGreaterThan(0);
  for (const path of paths) {
    await page.goto(source);
    const response = await Promise.all([
      page.waitForNavigation(),
      page.locator(`${selector}[href="${path}"]`).first().click(),
    ]);
    expect(response[0]?.status(), `${source} links to ${path}`).toBe(200);
    await expect(page.locator("main h1")).toBeVisible();
    if (retainMenu) {
      await expect(page).toHaveURL(path);
      await expect(page.locator(".section-nav")).toBeVisible();
      expect(await page.locator(".section-nav a").evaluateAll((links) =>
        links.map((link) => link.getAttribute("href")))).toEqual(paths);
      const widths = await page.evaluate(() => ({
        content: document.documentElement.scrollWidth,
        viewport: document.documentElement.clientWidth,
      }));
      expect(widths.content, `section navigation at ${path} fits ${widths.viewport} px`).toBeLessThanOrEqual(widths.viewport);
    }
  }
  await page.goto(source);
  return paths;
}

test("the public home connects the published pages and stays separate from developer tools", async ({ page }) => {
  await page.goto("/");
  await expect(page.getByRole("heading", { name: "Development index" })).toHaveCount(0);
  await expect(page.locator("main a[href^='/evenemang/']").first()).toBeVisible();
  await followLinks(page, ".site-nav a");
  await followLinks(page, "main a");
  await page.locator(".site-logo").click();
  await expect(page).toHaveURL("/");
  await expect(page.getByRole("heading", { name: "Development index" })).toHaveCount(0);
  await page.locator('.site-footer a[href="/admin"]').click();
  await expect(page).toHaveURL("/admin/logga-in");
  await page.goto("/dev");
  await expect(page.getByRole("heading", { name: "Development index" })).toBeVisible();
  await page.getByRole("link", { name: "Open the public start page" }).click();
  await expect(page).toHaveURL("/");
});

for (const width of [320, 2560]) {
  test.describe(`${width} px section navigation`, () => {
    test.use({ viewport: { width, height: 1300 } });

    for (const [kind, email] of [["member", MEMBERS.erik], ["administrator", ADMINISTRATORS.ada]] as const) {
      test(`${kind} keeps navigation throughout the completed sections`, async ({ page }) => {
        await linkLogin(page, kind, email);
        await followLinks(page, ".section-nav a", true);
      });
    }
  });
}

test("administrator pages link to Brevo where recipient groups and campaigns are managed", async ({ page }) => {
  await linkLogin(page, "administrator", ADMINISTRATORS.ada);
  await expect(page.locator('main a[href="https://www.sanity.io/manage"]')).toBeVisible();
  await expect(page.locator('main a[href="https://app.brevo.com/contact/segment"]')).toBeVisible();
  await expect(page.locator('main a[href="https://app.brevo.com/campaigns/listing/email"]')).toBeVisible();
  await page.locator('.section-nav a[href="/admin/utskick"]').click();
  await expect(page.locator('#audience-hint a[href="https://app.brevo.com/contact/segment"]')).toBeVisible();
  await expect(page.locator('main a[href="https://www.sanity.io/manage"]')).toBeVisible();
});
