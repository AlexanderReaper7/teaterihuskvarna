import { expect, type Page } from "@playwright/test";
import { text } from "./copy";
import { clearLinkRequests } from "./db";
import { clearMail, waitForMail } from "./mail";
import { PATHS, type Kind } from "./site";

/// Fills the login page's address field and submits it, as a person would.
export async function requestLink(page: Page, kind: Kind, email: string): Promise<void> {
  await page.goto(PATHS[kind].login);
  await page.locator("#email").fill(email);
  await page.locator(`form[action="${PATHS[kind].login}"] button[type=submit]`).click();
  await expect(page).toHaveURL(PATHS[kind].sent);
}

/// Asks for a link and returns it, without following it. The rate limit's
/// counts are cleared first, so a test can log in as often as it needs.
export async function mailedLink(page: Page, kind: Kind, email: string): Promise<string> {
  await clearLinkRequests();
  await clearMail(email);
  await requestLink(page, kind, email);
  return (await waitForMail(email)).link;
}

/// A whole login by link: ask, open the mailed link, and land on the kind's
/// home page. The link page submits itself with JavaScript.
export async function linkLogin(page: Page, kind: Kind, email: string): Promise<void> {
  const link = await mailedLink(page, kind, email);
  await page.goto(link);
  await expect(page).toHaveURL(PATHS[kind].home);
}

/// The page's own button, not the one in the row for this device in the list
/// of logged-in devices, whose name includes the device.
export async function logOut(page: Page, kind: Kind): Promise<void> {
  await page.locator(`form[action="${PATHS[kind].logout}"]`)
    .getByRole("button", { name: text("logout.submit"), exact: true }).click();
  await page.waitForLoadState("load");
}
