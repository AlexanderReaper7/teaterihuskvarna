// R016: an administrator deletes a volunteer shift from the shift's own page.
import { expect, test } from "@playwright/test";
import { linkLogin } from "../support/auth";
import { text } from "../support/copy";
import { clearLinkRequests, resetAdministrators, sql } from "../support/db";
import { ADMINISTRATORS, MEMBERS } from "../support/site";

const EVENT = "Borttagningstestets evenemang";

async function clear(): Promise<void> {
  await sql("DELETE FROM volunteer_shift WHERE event_title = $1", [EVENT]);
}

test.beforeEach(async () => {
  await resetAdministrators();
  await clearLinkRequests();
  await clear();
});
test.afterAll(clear);

test("deleting a shift asks first, says its booking goes too, and answering no keeps both", async ({ page }) => {
  const [shift] = await sql<{ id: string }>(`INSERT INTO volunteer_shift
    (event_id, event_title, event_slug, task, starts_at, ends_at, places, created_at)
    VALUES ('kulturnatten', $1, 'kulturnatten', 'GARDEROB', now() + interval '10 days',
      now() + interval '10 days 2 hours', 2, now()) RETURNING id`, [EVENT]);
  await sql(`INSERT INTO volunteer_booking (shift_id, member_id, created_at)
    SELECT $1, member_id, now() FROM account WHERE email = $2`, [shift.id, MEMBERS.erik]);
  const path = `/admin/volontarpass/${shift.id}`;
  const link = page.locator("main").getByRole("link", { name: text("adminShifts.delete"), exact: true });
  await linkLogin(page, "administrator", ADMINISTRATORS.ada);

  await page.goto(path);
  await link.click();
  await expect(page).toHaveURL(path + "/ta-bort");
  // adminShifts.deleteWarning for one booking. text() does not read a choice format.
  await expect(page.getByText("Passet och dess bokning tas bort. Volontären får inget meddelande.")).toBeVisible();
  await page.getByRole("link", { name: text("adminShifts.delete.cancel"), exact: true }).click();
  await expect(page).toHaveURL(path);
  expect(await sql("SELECT 1 FROM volunteer_booking WHERE shift_id = $1", [shift.id])).toHaveLength(1);

  await link.click();
  await page.locator("main").getByRole("button", { name: text("adminShifts.delete.submit"), exact: true }).click();
  await expect(page).toHaveURL("/admin/volontarpass");
  await expect(page.getByRole("status")).toHaveText(text("adminShifts.deleted"));
  expect(await sql("SELECT 1 FROM volunteer_shift WHERE id = $1", [shift.id])).toHaveLength(0);
  expect(await sql("SELECT 1 FROM volunteer_booking WHERE shift_id = $1", [shift.id])).toHaveLength(0);
});
