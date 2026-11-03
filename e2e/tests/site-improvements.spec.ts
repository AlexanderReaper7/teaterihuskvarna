import { expect, test } from "@playwright/test";
import { linkLogin } from "../support/auth";
import { clearLinkRequests, resetAdministrators } from "../support/db";
import { ADMINISTRATORS } from "../support/site";

test.beforeEach(async () => {
  await resetAdministrators();
  await clearLinkRequests();
});

test("the membership application starts within the first phone screen", async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto("/bli-medlem");
  const name = await page.locator("#fullName").boundingBox();
  expect(name).not.toBeNull();
  expect(name!.y).toBeLessThan(600);
  await expect(page.locator("main")).toContainText("50 kr");
  await expect(page.locator("main")).toContainText("100 kr");
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(390);
});

test("administrative tables use desktop space without widening public pages", async ({ page }) => {
  await page.setViewportSize({ width: 2560, height: 1440 });
  await linkLogin(page, "administrator", ADMINISTRATORS.ada);
  await page.goto("/admin/medlemmar");
  const table = await page.locator("main table").boundingBox();
  expect(table!.width).toBeGreaterThan(1200);
  await page.goto("/om-foreningen");
  const publicPage = await page.locator("main").boundingBox();
  expect(publicPage!.width).toBeLessThanOrEqual(1088);
});

test("a public article has sharing metadata and the sitemap lists only public content", async ({ page }) => {
  await page.goto("/nyheter/ny-webbplats");
  await expect(page.locator('meta[name="description"]')).toHaveAttribute("content", /\S/);
  await expect(page.locator('meta[property="og:title"]')).toHaveAttribute("content", "Ny webbplats");
  await expect(page.locator('meta[property="og:type"]')).toHaveAttribute("content", "article");
  await expect(page.locator('meta[property="og:image"]')).toHaveAttribute("content", /^http/);
  const response = await page.request.get("/sitemap.xml");
  expect(response.status()).toBe(200);
  const xml = await response.text();
  expect(xml).toContain("/nyheter/ny-webbplats</loc>");
  expect(xml).not.toMatch(/\/medlem|\/admin|\/nyheter\/framtida/);
});
