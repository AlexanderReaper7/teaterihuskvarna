import AxeBuilder from "@axe-core/playwright";
import { expect, test } from "@playwright/test";
import { clearMail, mailCountAfterWait } from "../support/mail";
import { MEMBERS } from "../support/site";

test("the development index immediately logs in either account kind on the same address", async ({ page }) => {
  await clearMail(MEMBERS.karin);
  for (const [kind, home, api] of [["member", "/medlem", "/api/member"],
    ["administrator", "/admin", "/api/admin/administrators"], ["member", "/medlem", "/api/member"]]) {
    await page.goto("/dev");
    const row = page.getByRole("row")
      .filter({ has: page.getByRole("cell", { name: MEMBERS.karin, exact: true }) })
      .filter({ has: page.getByRole("cell", { name: kind, exact: true }) });
    await expect(row.getByRole("button", { name: "Send link", exact: true })).toBeVisible();
    await row.getByRole("button", { name: "Log in now", exact: true }).click();
    await expect(page).toHaveURL(home);
    expect((await page.request.get(api)).status()).toBe(200);
    await page.reload();
    await expect(page).toHaveURL(home);
    await page.goto("/dev");
    await expect(page.locator("main dl")).toContainText(`as ${kind}`);
  }
  expect(await mailCountAfterWait(MEMBERS.karin)).toBe(0);
  const axe = await new AxeBuilder({ page }).withTags(["wcag2a", "wcag2aa", "wcag21a", "wcag21aa"]).analyze();
  expect(axe.violations).toEqual([]);
});
