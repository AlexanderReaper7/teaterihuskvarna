// The list of logged-in devices, and the countdown before a login ends, which
// every logged-in page carries. docs/decisions/0015, "A login ends a fixed
// time after it starts". Runs in every browser.
import { expect, test, type Page } from "@playwright/test";
import { linkLogin } from "../support/auth";
import { text } from "../support/copy";
import { clearLinkRequests, clearSessions, resetAdministrators } from "../support/db";
import { ADMIN, MEMBER } from "../support/pages";
import { ADMINISTRATORS, MEMBERS, PATHS, type Kind } from "../support/site";

test.beforeEach(async () => {
  await resetAdministrators();
  await clearLinkRequests();
  await clearSessions();
});

const devices = (page: Page) =>
  page.locator("section", { has: page.getByRole("heading", { name: text("device.heading") }) });
const ending = (page: Page) => page.locator("[data-login-ending]");
const ended = (page: Page) => page.locator("[data-login-ended]");

const API = { member: "/api/member", administrator: "/api/admin" } as const;

/// Opens `path` as if its login had `seconds` left, and answers the status
/// question with `status`, so a test need not wait 30 days or 8 hours.
/// Extending is not answered here: it reaches the server.
async function endingIn(page: Page, kind: Kind, path: string, seconds: number,
  status: { secondsLeft: number } | 401): Promise<void> {
  await page.route(`**${path}`, async (route) => {
    const response = await route.fetch();
    const body = (await response.text()).replace(/data-seconds-left="\d+"/, `data-seconds-left="${seconds}"`);
    await route.fulfill({ response, body });
  });
  await page.route(`**${API[kind]}/session`, (route) =>
    status === 401 ? route.fulfill({ status: 401 }) : route.fulfill({ json: { secondsLeft: status.secondsLeft } }));
  await page.goto(path);
}

/// Presses "Fortsätt vara inloggad" and returns the seconds the server then gives the login.
async function extend(page: Page, kind: Kind): Promise<number> {
  const extended = page.waitForResponse((response) =>
    response.url().endsWith(`${API[kind]}/session/extend`) && response.request().method() === "POST");
  await ending(page).getByRole("button", { name: text("session.extend") }).click();
  expect((await extended).status()).toBe(200);
  await expect(ending(page)).toBeHidden();
  return (await (await extended).json()).secondsLeft;
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
    await endingIn(page, "member", PATHS.member.home, 100, { secondsLeft: 100 });

    await expect(ending(page)).toBeVisible();
    await expect(ending(page).getByText(text("session.ending"))).toBeVisible();
    await expect(ending(page)).toContainText(new RegExp(text("session.remaining", "1:[0-4]\\d")));

    // The server's side of extending is in DeviceIT; here, that the page asks for it and is let through.
    expect(await extend(page, "member")).toBeGreaterThan(30 * 86_400 - 60);
  });

test("at the end the page stays and links to the plain login page", async ({ page }) => {
  await linkLogin(page, "member", MEMBERS.anders);
  await endingIn(page, "member", PATHS.member.home, 1, 401);

  await expect(ended(page)).toBeVisible();
  await expect(ending(page)).toBeHidden();
  await expect(ended(page).getByRole("link", { name: text("session.login") }))
    .toHaveAttribute("href", PATHS.member.login);
  await expect(page).toHaveURL(PATHS.member.home);
});

// A login can end while its owner is on any page, most expensively on one
// with a form half filled in. So the warning is on every logged-in page, and
// extending asks the server without loading the page again.

test("every member page carries the countdown", async ({ page }) => {
  await linkLogin(page, "member", MEMBERS.sara);
  for (const path of MEMBER) {
    await page.goto(path);
    await expect(page.locator("[data-login-countdown]"), path).toHaveCount(1);
    await expect(ending(page), path).toBeHidden();
  }
});

test("every administrator page carries the countdown", async ({ page }) => {
  await linkLogin(page, "administrator", ADMINISTRATORS.ada);
  for (const path of ADMIN) {
    await page.goto(path);
    await expect(page.locator("[data-login-countdown]"), path).toHaveCount(1);
    await expect(ending(page), path).toBeHidden();
  }
});

test("an administrator writing a mailing is warned, and extending keeps what is typed", async ({ page }) => {
  await linkLogin(page, "administrator", ADMINISTRATORS.ada);
  await endingIn(page, "administrator", "/admin/utskick", 100, { secondsLeft: 100 });
  await page.locator("#subject").fill("Höstens program");
  await page.locator("#intro").fill("Hej alla!\n\nNu drar vi igång.");

  await expect(ending(page)).toBeVisible();
  await expect(ending(page).getByText(text("session.ending"))).toBeVisible();
  expect(await extend(page, "administrator")).toBeGreaterThan(8 * 3600 - 60);

  await expect(page.locator("#subject")).toHaveValue("Höstens program");
  await expect(page.locator("#intro")).toHaveValue("Hej alla!\n\nNu drar vi igång.");
  await expect(page).toHaveURL("/admin/utskick");
});

test("a member changing contact details is warned, and extending keeps what is typed", async ({ page }) => {
  await linkLogin(page, "member", MEMBERS.johan);
  await endingIn(page, "member", "/medlem/kontaktuppgifter", 100, { secondsLeft: 100 });
  await page.locator("#address").fill("Teatergatan 1");

  await expect(ending(page)).toBeVisible();
  expect(await extend(page, "member")).toBeGreaterThan(30 * 86_400 - 60);

  await expect(page.locator("#address")).toHaveValue("Teatergatan 1");
  await expect(page).toHaveURL("/medlem/kontaktuppgifter");
});

// The countdown carries the token its request needs. It used to borrow one
// from a form on the page, and this page has no form.
test("extending works on a page that has no form", async ({ page }) => {
  await linkLogin(page, "member", MEMBERS.johan);
  await endingIn(page, "member", "/medlem/handlingar", 100, { secondsLeft: 100 });
  await expect(page.locator("form"), "the premise: no form on this page").toHaveCount(0);

  await expect(ending(page)).toBeVisible();
  expect(await extend(page, "member")).toBeGreaterThan(30 * 86_400 - 60);
});

test("when a login ends on a page with a form, the page stays with what was typed", async ({ page }) => {
  await linkLogin(page, "administrator", ADMINISTRATORS.ada);
  await endingIn(page, "administrator", "/admin/utskick", 1, 401);
  await page.locator("#subject").fill("Höstens program");

  await expect(ended(page)).toBeVisible();
  await expect(ended(page).getByRole("link", { name: text("session.login") }))
    .toHaveAttribute("href", PATHS.administrator.login);
  await expect(page.locator("#subject")).toHaveValue("Höstens program");
  await expect(page).toHaveURL("/admin/utskick");
});
