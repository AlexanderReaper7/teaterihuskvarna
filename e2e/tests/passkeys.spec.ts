// Passkeys beside login links: docs/decisions/0016. Chromium only, because the
// software authenticator is its DevTools protocol; playwright.config.ts keeps
// this file out of the other browsers.
import { expect, test, type Page } from "@playwright/test";
import { linkLogin, logOut } from "../support/auth";
import { text } from "../support/copy";
import { clearLinkRequests, clearPasskeys, resetAdministrators } from "../support/db";
import { addAuthenticator, withoutAutofill } from "../support/webauthn";
import { ADMINISTRATORS, MEMBERS, PATHS, type Kind } from "../support/site";

test.beforeEach(async () => {
  await resetAdministrators();
  await clearLinkRequests();
  await clearPasskeys();
});

const offer = (page: Page) => page.locator("[data-passkey-offer]");
const section = (page: Page) => page.locator("[data-passkeys]");
const listed = (page: Page) => section(page).locator("tbody tr");
const loginError = (page: Page) => page.locator("[data-passkey-login] [data-passkey-error]");

/// Adds a passkey from the section's button and waits for the page to reload
/// with it listed.
async function addFromSection(page: Page, expected: number): Promise<void> {
  await section(page).getByRole("button", { name: text("passkey.add") }).click();
  await expect(listed(page)).toHaveCount(expected);
}

async function buttonLogin(page: Page, kind: Kind): Promise<void> {
  await page.goto(PATHS[kind].login);
  // Any error seen after this is the button's.
  await expect(loginError(page)).toBeHidden();
  await page.getByRole("button", { name: text("login.passkey.submit") }).click();
}

test.describe("what works", () => {
  test("a member adds a passkey from the offer and it is listed with the browser's name", async ({ page }) => {
    const authenticator = await addAuthenticator(page);
    await linkLogin(page, "member", MEMBERS.erik);
    await expect(offer(page)).toBeVisible();
    await offer(page).getByRole("button", { name: text("passkey.add") }).click();

    await expect(listed(page)).toHaveCount(1);
    // Playwright's "Desktop Chrome" sends a Windows user agent, whatever the
    // machine running it.
    await expect(listed(page).first()).toContainText(text("passkey.label", "Chrome", "Windows"));
    await expect(offer(page)).toBeHidden();
    const credentials = await authenticator.credentials();
    expect(credentials).toHaveLength(1);
    expect(credentials[0].isResidentCredential).toBe(true);
  });

  test("the passkey button logs in, and no offer follows", async ({ page, context }) => {
    await withoutAutofill(context);
    await addAuthenticator(page);
    await linkLogin(page, "member", MEMBERS.erik);
    await addFromSection(page, 1);
    await logOut(page, "member");

    await buttonLogin(page, "member");
    await expect(page).toHaveURL(PATHS.member.home);
    await expect(page.getByText("Erik Lindqvist", { exact: true })).toBeVisible();
    await expect(offer(page)).toHaveCount(0);
  });

  test("the address field's autofill logs in as soon as the page opens", async ({ page }) => {
    await addAuthenticator(page);
    await linkLogin(page, "member", MEMBERS.maria);
    await addFromSection(page, 1);
    await logOut(page, "member");

    await page.goto(PATHS.member.login);
    await expect(page).toHaveURL(PATHS.member.home);
    await expect(page.getByText("Maria Lindqvist", { exact: true })).toBeVisible();
  });

  test("an administrator adds a passkey and logs in with it", async ({ page, context }) => {
    await withoutAutofill(context);
    await addAuthenticator(page);
    await linkLogin(page, "administrator", ADMINISTRATORS.gunnar);
    await addFromSection(page, 1);
    await logOut(page, "administrator");

    await buttonLogin(page, "administrator");
    await expect(page).toHaveURL(PATHS.administrator.home);
    await expect(page.getByText(text("admin.signedInAs", "Gunnar Wik"))).toBeVisible();
  });

  test("the browser saves an administrator's passkey under the address and (administratör)", async ({ page }) => {
    const authenticator = await addAuthenticator(page);
    await linkLogin(page, "administrator", ADMINISTRATORS.gunnar);
    await addFromSection(page, 1);
    const [credential] = await authenticator.credentials();
    expect(credential.userName).toBe(text("passkey.name.administrator", ADMINISTRATORS.gunnar));
  });
});

test.describe("what a person might get wrong", () => {
  test("a member's passkey on the administrator login page is refused with a message", async ({ page, context }) => {
    await withoutAutofill(context);
    await addAuthenticator(page);
    await linkLogin(page, "member", MEMBERS.erik);
    await addFromSection(page, 1);
    await logOut(page, "member");

    await buttonLogin(page, "administrator");
    await expect(loginError(page)).toHaveText(text("passkey.error.login"));
    await expect(page).toHaveURL(PATHS.administrator.login);
  });

  test("a removed passkey no longer logs in", async ({ page, context }) => {
    await withoutAutofill(context);
    await addAuthenticator(page);
    await linkLogin(page, "member", MEMBERS.erik);
    await addFromSection(page, 1);
    await listed(page).first().getByRole("button").click();
    await expect(page.getByRole("status")).toHaveText(text("passkey.removed"));
    await expect(listed(page)).toHaveCount(0);
    await logOut(page, "member");

    // The authenticator still holds it, as a phone would until the person
    // deletes it there too.
    await buttonLogin(page, "member");
    await expect(loginError(page)).toHaveText(text("passkey.error.login"));
    await expect(page).toHaveURL(PATHS.member.login);
  });

  test("closing the browser's passkey dialog says nothing was added", async ({ page }) => {
    const authenticator = await addAuthenticator(page);
    await authenticator.setUserVerified(false);
    await linkLogin(page, "member", MEMBERS.johan);
    await section(page).getByRole("button", { name: text("passkey.add") }).click();
    await expect(section(page).locator("[data-passkey-error]")).toHaveText(text("passkey.error.cancelled"));
    await expect(listed(page)).toHaveCount(0);
  });

  test("adding a second passkey on the same device says it already has one", async ({ page }) => {
    await addAuthenticator(page);
    await linkLogin(page, "member", MEMBERS.sara);
    await addFromSection(page, 1);
    await section(page).getByRole("button", { name: text("passkey.add") }).click();
    await expect(section(page).locator("[data-passkey-error]")).toHaveText(text("passkey.error.exists"));
    await expect(listed(page)).toHaveCount(1);
  });

  test("a browser without passkeys says so and hides the buttons", async ({ page, context }) => {
    await context.addInitScript(() => {
      delete (window as { PublicKeyCredential?: unknown }).PublicKeyCredential;
    });
    await linkLogin(page, "member", MEMBERS.anders);
    await expect(section(page).getByText(text("passkey.unsupported"))).toBeVisible();
    await expect(page.getByRole("button", { name: text("passkey.add") })).toHaveCount(0);
    await expect(offer(page)).toBeHidden();

    await logOut(page, "member");
    await page.goto(PATHS.member.login);
    await expect(page.getByRole("button", { name: text("login.passkey.submit") })).toBeHidden();
  });

  test("one member's passkeys are not listed for another", async ({ page, browser }) => {
    await addAuthenticator(page);
    await linkLogin(page, "member", MEMBERS.erik);
    await addFromSection(page, 1);

    const other = await browser.newContext();
    const otherPage = await other.newPage();
    await linkLogin(otherPage, "member", MEMBERS.maria);
    await expect(section(otherPage).getByText(text("passkey.none"))).toBeVisible();
    await other.close();
  });

  test("one member cannot remove another's passkey by its id", async ({ page, browser }) => {
    await addAuthenticator(page);
    await linkLogin(page, "member", MEMBERS.erik);
    await addFromSection(page, 1);
    const action = await listed(page).first().locator("form").getAttribute("action");

    const other = await browser.newContext();
    const otherPage = await other.newPage();
    await linkLogin(otherPage, "member", MEMBERS.maria);
    const csrf = await otherPage.locator("input[name=_csrf]").first().inputValue();
    await otherPage.request.post(action!, { form: { _csrf: csrf } });
    await other.close();

    await page.reload();
    await expect(listed(page)).toHaveCount(1);
  });

  test("removing in a second tab a passkey the first tab removed says it is gone", async ({ page, context }) => {
    await addAuthenticator(page);
    await linkLogin(page, "member", MEMBERS.karin);
    await addFromSection(page, 1);
    const second = await context.newPage();
    await second.goto(PATHS.member.home);

    await listed(page).first().getByRole("button").click();
    await expect(page.getByRole("status")).toHaveText(text("passkey.removed"));
    await listed(second).first().getByRole("button").click();
    await expect(second.getByRole("alert")).toHaveText(text("passkey.error.noSuch"));
  });

  test("removing an administrator removes their passkey", async ({ page, browser }) => {
    const gunnar = await browser.newContext();
    await withoutAutofill(gunnar);
    const gunnarPage = await gunnar.newPage();
    await addAuthenticator(gunnarPage);
    await linkLogin(gunnarPage, "administrator", ADMINISTRATORS.gunnar);
    await addFromSection(gunnarPage, 1);

    await linkLogin(page, "administrator", ADMINISTRATORS.ada);
    await page.getByRole("button", { name: text("admin.remove.label", "Gunnar Wik"), exact: true }).click();
    await expect(page.getByRole("status")).toHaveText(text("admin.removed"));

    await buttonLogin(gunnarPage, "administrator");
    await expect(loginError(gunnarPage)).toHaveText(text("passkey.error.login"));
    await gunnar.close();
  });
});
