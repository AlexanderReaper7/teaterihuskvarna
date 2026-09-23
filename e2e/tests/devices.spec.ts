// The list of logged-in devices, and the countdown before a login ends.
// docs/decisions/0015, "A login ends a fixed time after it starts". Runs in
// every browser.
import { expect, test, type Page } from "@playwright/test";
import { linkLogin } from "../support/auth";
import { text } from "../support/copy";
import { clearLinkRequests, clearSessions, resetAdministrators } from "../support/db";
import { MEMBERS, PATHS } from "../support/site";

test.beforeEach(async () => {
  await resetAdministrators();
  await clearLinkRequests();
  await clearSessions();
});

const devices = (page: Page) =>
  page.locator("section", { has: page.getByRole("heading", { name: text("device.heading") }) });
const ending = (page: Page) => page.locator("[data-login-ending]");
const ended = (page: Page) => page.locator("[data-login-ended]");

/// Serves the member page as if its login had `seconds` left, and answers the
/// status question with `status`, so a test need not wait 30 days.
async function endingIn(page: Page, seconds: number, status: { secondsLeft: number } | 401): Promise<void> {
  await page.route(`**${PATHS.member.home}`, async (route) => {
    const response = await route.fetch();
    const body = (await response.text()).replace(/data-seconds-left="\d+"/, `data-seconds-left="${seconds}"`);
    await route.fulfill({ response, body });
  });
  await page.route("**/api/member/session", (route) =>
    status === 401 ? route.fulfill({ status: 401 }) : route.fulfill({ json: { secondsLeft: status.secondsLeft } }));
  await page.reload();
}

test("a device logged out from another shows it is logged out, with the plain login page",
  async ({ page, browser }) => {
    const phone = await browser.newContext();
    const phonePage = await phone.newPage();
    await phonePage.clock.install();
    await linkLogin(phonePage, "member", MEMBERS.sara);
    await linkLogin(page, "member", MEMBERS.sara);

    const rows = devices(page).locator("tbody tr");
    await expect(rows).toHaveCount(2);
    await expect(rows.first()).toContainText(text("device.current"));
    await expect(rows.nth(1)).not.toContainText(text("device.current"));
    await rows.nth(1).getByRole("button", { name: text("device.end") }).click();
    await expect(page.getByText(text("device.ended"))).toBeVisible();
    await expect(devices(page).locator("tbody tr")).toHaveCount(1);

    // The phone's page asks again when it comes back into view a minute later.
    await phonePage.clock.fastForward(61_000);
    await phonePage.evaluate(() => document.dispatchEvent(new Event("visibilitychange")));
    await expect(ended(phonePage)).toBeVisible();
    await expect(ended(phonePage).getByText(text("session.ended"))).toBeVisible();
    await expect(ended(phonePage).getByRole("link", { name: text("session.login") }))
      .toHaveAttribute("href", PATHS.member.login);
    await expect(phonePage).toHaveURL(PATHS.member.home);
    await phone.close();
  });

test("two minutes before the end the page counts down, and extending asks the server for 30 days more",
  async ({ page }) => {
    await linkLogin(page, "member", MEMBERS.johan);
    await expect(ending(page)).toBeHidden();
    await endingIn(page, 100, { secondsLeft: 100 });

    await expect(ending(page)).toBeVisible();
    await expect(ending(page).getByText(text("session.ending"))).toBeVisible();
    await expect(ending(page)).toContainText(new RegExp(text("session.remaining", "1:[0-4]\\d")));

    // The server's side of extending is in DeviceIT; here, that the page asks for it and is let through.
    const extended = page.waitForResponse((response) =>
      response.url().endsWith("/api/member/session/extend") && response.request().method() === "POST");
    await ending(page).getByRole("button", { name: text("session.extend") }).click();
    expect((await extended).status()).toBe(200);
    expect((await (await extended).json()).secondsLeft).toBeGreaterThan(30 * 86_400 - 60);
    await expect(ending(page)).toBeHidden();
  });

test("at the end the page stays and links to the plain login page", async ({ page }) => {
  await linkLogin(page, "member", MEMBERS.anders);
  await endingIn(page, 1, 401);

  await expect(ended(page)).toBeVisible();
  await expect(ending(page)).toBeHidden();
  await expect(ended(page).getByRole("link", { name: text("session.login") }))
    .toHaveAttribute("href", PATHS.member.login);
  await expect(page).toHaveURL(PATHS.member.home);
});
