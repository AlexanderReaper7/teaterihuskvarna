// The offer to add a passkey after a link login: "Inte nu" and "Fråga inte
// igen". docs/decisions/0016, "Declining is per browser". Runs in every
// browser, since it needs no authenticator.
import { expect, test, type Page } from "@playwright/test";
import { linkLogin } from "../support/auth";
import { text } from "../support/copy";
import { clearLinkRequests, resetAdministrators } from "../support/db";
import { MEMBERS, PATHS } from "../support/site";

test.beforeEach(async () => {
  await resetAdministrators();
  await clearLinkRequests();
});

const offer = (page: Page) => page.locator("[data-passkey-offer]");

test("the offer shows once after a link login, and not on the next visit", async ({ page }) => {
  await linkLogin(page, "member", MEMBERS.erik);
  await expect(offer(page)).toBeVisible();
  await expect(offer(page).getByText(text("passkey.offer"))).toBeVisible();
  await page.reload();
  await expect(offer(page)).toHaveCount(0);
});

test("Inte nu hides the offer until the next link login", async ({ page }) => {
  await linkLogin(page, "member", MEMBERS.maria);
  await offer(page).getByRole("button", { name: text("passkey.offer.dismiss") }).click();
  await expect(offer(page)).toBeHidden();
  await linkLogin(page, "member", MEMBERS.maria);
  await expect(offer(page)).toBeVisible();
});

test("Fråga inte igen stops the member offer on this browser only", async ({ page, context, browser }) => {
  await linkLogin(page, "member", MEMBERS.karin);
  await offer(page).getByRole("button", { name: text("passkey.offer.decline") }).click();
  await expect(page).toHaveURL(PATHS.member.home);
  await expect(offer(page)).toHaveCount(0);

  const cookie = (await context.cookies()).find((c) => c.name === "p");
  expect(cookie).toMatchObject({ value: "1", path: "/medlem", httpOnly: true, sameSite: "Lax" });
  const days = (cookie!.expires * 1000 - Date.now()) / 86_400_000;
  expect(days).toBeGreaterThan(399);
  expect(days).toBeLessThanOrEqual(400);

  await linkLogin(page, "member", MEMBERS.karin);
  await expect(offer(page)).toHaveCount(0);

  // The same person as an administrator, in the same browser, is still asked.
  await linkLogin(page, "administrator", MEMBERS.karin);
  await expect(offer(page)).toBeVisible();

  // Another browser is still asked as a member.
  const phone = await browser.newContext();
  const phonePage = await phone.newPage();
  await linkLogin(phonePage, "member", MEMBERS.karin);
  await expect(offer(phonePage)).toBeVisible();
  await phone.close();
});

test("clearing the browser's cookies brings the offer back", async ({ page, context }) => {
  await linkLogin(page, "member", MEMBERS.johan);
  await offer(page).getByRole("button", { name: text("passkey.offer.decline") }).click();
  await expect(offer(page)).toHaveCount(0);
  await context.clearCookies();
  await linkLogin(page, "member", MEMBERS.johan);
  await expect(offer(page)).toBeVisible();
});
