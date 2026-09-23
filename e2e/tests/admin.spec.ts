// Administrators adding and removing each other on /admin.
import { expect, test, type Page } from "@playwright/test";
import { linkLogin } from "../support/auth";
import { text } from "../support/copy";
import { activeAdministrators, clearLinkRequests, resetAdministrators } from "../support/db";
import { clearMail, mailCountAfterWait } from "../support/mail";
import { ADMINISTRATORS, freshAddress, MEMBERS, PATHS } from "../support/site";

test.beforeEach(async () => {
  await resetAdministrators();
  await clearLinkRequests();
});
test.afterAll(resetAdministrators);

async function add(page: Page, fullName: string, email: string): Promise<void> {
  await page.getByLabel(text("admin.add.fullName"), { exact: true }).fill(fullName);
  await page.getByLabel(text("admin.add.email"), { exact: true }).fill(email);
  await page.getByRole("button", { name: text("admin.add.submit"), exact: true }).click();
}

const removeButton = (page: Page, fullName: string) =>
  page.getByRole("button", { name: text("admin.remove.label", fullName), exact: true });

const rows = (page: Page) => page.getByRole("table", { name: text("admin.list.caption") }).locator("tbody tr");

test.describe("what works", () => {
  test("an administrator adds another, who can then log in", async ({ page, browser }) => {
    const email = freshAddress("ny-admin");
    await linkLogin(page, "administrator", ADMINISTRATORS.ada);
    await add(page, "Ny Administratör", email);
    await expect(page).toHaveURL(PATHS.administrator.home);
    await expect(page.getByRole("status")).toHaveText(text("admin.added", "Ny Administratör"));
    await expect(rows(page).filter({ hasText: email })).toHaveCount(1);

    const theirs = await browser.newContext();
    const theirPage = await theirs.newPage();
    await linkLogin(theirPage, "administrator", email);
    await expect(theirPage.getByText(text("admin.signedInAs", "Ny Administratör"))).toBeVisible();
    await theirs.close();
  });

  test("a removed administrator is logged out at once and gets no more links", async ({ page, browser }) => {
    const email = freshAddress("tas-bort");
    await linkLogin(page, "administrator", ADMINISTRATORS.ada);
    await add(page, "Tas Bort", email);

    const theirs = await browser.newContext();
    const theirPage = await theirs.newPage();
    await linkLogin(theirPage, "administrator", email);

    await removeButton(page, "Tas Bort").click();
    await expect(page.getByRole("status")).toHaveText(text("admin.removed"));
    await expect(rows(page).filter({ hasText: email })).toHaveCount(0);

    await theirPage.reload();
    await expect(theirPage).toHaveURL(PATHS.administrator.login);
    await clearMail(email);
    await theirPage.locator("#email").fill(email);
    await theirPage.locator(`form[action="${PATHS.administrator.login}"] button[type=submit]`).click();
    await expect(theirPage).toHaveURL(PATHS.administrator.sent);
    expect(await mailCountAfterWait(email)).toBe(0);
    await theirs.close();
  });

  test("a removed administrator can be added back", async ({ page }) => {
    await linkLogin(page, "administrator", ADMINISTRATORS.ada);
    await removeButton(page, "Gunnar Wik").click();
    await expect(page.getByRole("status")).toHaveText(text("admin.removed"));
    await add(page, "Gunnar Wik", ADMINISTRATORS.gunnar);
    await expect(page.getByRole("status")).toHaveText(text("admin.added", "Gunnar Wik"));
    expect(await activeAdministrators()).toContain(ADMINISTRATORS.gunnar);
  });
});

test.describe("what a person might get wrong", () => {
  test("adding an existing administrator's address in other capitals is refused", async ({ page }) => {
    await linkLogin(page, "administrator", ADMINISTRATORS.ada);
    await add(page, "Gunnar Igen", "GUNNAR.WIK@example.test");
    await expect(page.getByRole("alert")).toHaveText(text("admin.error.alreadyExists"));
    await expect(page.locator("#fullName")).toHaveValue("Gunnar Igen");
    expect(await activeAdministrators()).toHaveLength(3);
  });

  test("a name of only spaces is refused beside the field", async ({ page }) => {
    await linkLogin(page, "administrator", ADMINISTRATORS.ada);
    const email = freshAddress("utan-namn");
    await add(page, "   ", email);
    await expect(page.locator("#fullName-error")).toHaveText(text("administrator.fullName.required"));
    await expect(page.locator("#email")).toHaveValue(email);
  });

  test("a name over 100 characters is refused beside the field", async ({ page }) => {
    await linkLogin(page, "administrator", ADMINISTRATORS.ada);
    await add(page, "B".repeat(101), freshAddress("langt-namn"));
    await expect(page.locator("#fullName-error")).toHaveText(text("administrator.fullName.size"));
  });

  test("a name with markup shows as text", async ({ page }) => {
    await linkLogin(page, "administrator", ADMINISTRATORS.ada);
    const name = `<img src=x onerror="window.injected=1">`;
    await add(page, name, freshAddress("markup-admin"));
    await expect(page.getByRole("status")).toHaveText(text("admin.added", name));
    await expect(rows(page).filter({ hasText: name })).toHaveCount(1);
    expect(await page.evaluate(() => (window as { injected?: number }).injected)).toBeUndefined();
  });

  test("removal stops at two administrators", async ({ page }) => {
    await linkLogin(page, "administrator", ADMINISTRATORS.ada);
    await removeButton(page, "Gunnar Wik").click();
    await expect(page.getByRole("status")).toHaveText(text("admin.removed"));
    await removeButton(page, "Karin Holmberg").click();
    await expect(page.getByRole("alert")).toHaveText(text("admin.error.tooFew"));
    await expect(rows(page)).toHaveCount(2);
  });

  test("an administrator who removes themselves is logged out", async ({ page }) => {
    await linkLogin(page, "administrator", ADMINISTRATORS.ada);
    await removeButton(page, "Ada Admin").click();
    await expect(page).toHaveURL(PATHS.administrator.login);
    expect(await activeAdministrators()).not.toContain(ADMINISTRATORS.ada);
  });

  test("pressing remove twice removes once and shows no error", async ({ page }) => {
    // Firefox sent both presses before forms.js; both stored the flash message
    // in the session at once, and the second insert failed with a 500.
    // SessionStoreIT covers that insert without depending on the browser.
    await linkLogin(page, "administrator", ADMINISTRATORS.ada);
    await removeButton(page, "Gunnar Wik").dblclick();
    await expect(page).toHaveURL(PATHS.administrator.home);
    await expect(rows(page).filter({ hasText: ADMINISTRATORS.gunnar })).toHaveCount(0);
    await expect(page.getByRole("status")).toHaveText(text("admin.removed"));
    await expect(page.getByRole("alert")).toHaveCount(0);
  });

  test("removing in a second tab someone the first tab removed says they are gone", async ({ page, context }) => {
    await linkLogin(page, "administrator", ADMINISTRATORS.ada);
    const second = await context.newPage();
    await second.goto(PATHS.administrator.home);
    await removeButton(page, "Gunnar Wik").click();
    await expect(page.getByRole("status")).toHaveText(text("admin.removed"));
    await removeButton(second, "Gunnar Wik").click();
    await expect(second.getByRole("alert")).toHaveText(text("admin.error.noSuch"));
  });

  test("a member's session cannot add an administrator", async ({ page }) => {
    await linkLogin(page, "member", MEMBERS.erik);
    const csrf = await page.locator("input[name=_csrf]").first().inputValue();
    const response = await page.request.post(`${PATHS.administrator.home}/administratorer`, {
      form: { _csrf: csrf, fullName: "Inkräktare", email: freshAddress("inkraktare") },
      maxRedirects: 0,
    });
    expect([302, 401, 403]).toContain(response.status());
    expect(await activeAdministrators()).toHaveLength(3);
  });
});
