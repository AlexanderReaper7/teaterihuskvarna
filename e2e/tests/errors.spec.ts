// The error page, and the 404 a path no access rule mentions gets whoever asks.
import { expect, test, type Page } from "@playwright/test";
import { linkLogin } from "../support/auth";
import { text } from "../support/copy";
import { clearLinkRequests, resetAdministrators } from "../support/db";
import { ADMINISTRATORS, MEMBERS } from "../support/site";

test.beforeEach(async () => {
  await resetAdministrators();
  await clearLinkRequests();
});

/// Opens `path` and checks it is the 404 page, with the status to match.
async function expectNotFound(page: Page, path: string): Promise<void> {
  const response = await page.goto(path);
  expect(response?.status(), path).toBe(404);
  await expect(page.getByRole("heading", { level: 1 })).toHaveText(text("error.notFound.heading"));
}

test.describe("what a person might get wrong", () => {
  test("a visitor on an address that does not exist gets the 404 page and a way home", async ({ page }) => {
    await expectNotFound(page, "/finns-inte");
    await page.getByRole("link", { name: text("error.home"), exact: true }).click();
    await expect(page).toHaveURL("/");
  });

  test("a logged-in member on an address that does not exist gets the 404 page", async ({ page }) => {
    await linkLogin(page, "member", MEMBERS.erik);
    await expectNotFound(page, "/finns-inte");
  });

  test("an administrator gets the 404 page outside /admin and inside it", async ({ page }) => {
    await linkLogin(page, "administrator", ADMINISTRATORS.ada);
    await expectNotFound(page, "/finns-inte");
    await expectNotFound(page, "/admin/finns-inte");
  });
});
