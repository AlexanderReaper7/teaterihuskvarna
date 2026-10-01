import AxeBuilder from "@axe-core/playwright";
import { expect, test, type Page } from "@playwright/test";
import { linkLogin } from "../support/auth";
import { sql } from "../support/db";
import { ADMINISTRATORS, freshAddress } from "../support/site";

async function member(name: string, email: string, household: string | null = null): Promise<string> {
  const [person] = await sql<{ id: string }>(
    "INSERT INTO member (full_name, household_id, created_at) VALUES ($1, $2, now()) RETURNING id",
    [name, household]);
  await sql("INSERT INTO account (member_id, email, created_at) VALUES ($1, $2, now())", [person.id, email]);
  return person.id;
}

async function accessible(page: Page): Promise<void> {
  const result = await new AxeBuilder({ page }).withTags(["wcag2a", "wcag2aa", "wcag21a", "wcag21aa"]).analyze();
  expect(result.violations.map((violation) => `${violation.id}: ${violation.nodes.map((node) => node.target)}`))
    .toEqual([]);
  const widths = await page.evaluate(() => ({
    content: document.documentElement.scrollWidth,
    viewport: document.documentElement.clientWidth,
  }));
  expect(widths.content).toBeLessThanOrEqual(widths.viewport);
}

for (const width of [320, 2560]) {
  test.describe(`${width} px household management`, () => {
    test.use({ viewport: { width, height: 1300 } });

    test("a member creates, renames and manages people with and without accounts", async ({ page }) => {
      const email = freshAddress("household");
      const ownId = await member("Erik Exempel", email);
      await linkLogin(page, "member", email);
      await page.getByRole("link", { name: "Skapa ett hushåll", exact: true }).click();
      await accessible(page);
      await page.getByLabel("Namn, till exempel Familjen Lindqvist").fill("Familjen Exempel");
      await page.getByRole("button", { name: "Skapa", exact: true }).click();
      await expect(page.getByRole("status")).toContainText("Hushållet Familjen Exempel är skapat.");
      await page.getByLabel("Namn, till exempel Familjen Lindqvist").fill("Familjen Nytt namn");
      await page.getByRole("button", { name: "Spara hushållets namn" }).click();
      await expect(page.getByRole("status")).toContainText("Hushållets namn är sparat.");
      await accessible(page);

      await page.getByRole("link", { name: "Lägg till en person" }).click();
      await accessible(page);
      await page.getByLabel("Namn", { exact: true }).fill("Olle Exempel");
      await page.getByLabel("Ort", { exact: true }).fill("Huskvarna");
      await page.getByRole("button", { name: "Spara", exact: true }).click();
      await page.getByRole("link", { name: "Ändra uppgifter för Olle Exempel" }).click();
      await expect(page.getByLabel("Ort", { exact: true })).toHaveValue("Huskvarna");
      await page.getByLabel("Namn", { exact: true }).fill("Olle Nytt namn");
      await page.getByRole("button", { name: "Spara", exact: true }).click();
      await page.getByRole("link", { name: "Tillbaka till Mina uppgifter" }).click();
      await expect(page.getByLabel("E-postadress till Olle Nytt namn")).toBeVisible();

      const [own] = await sql<{ household_id: string }>("SELECT household_id FROM member WHERE id = $1", [ownId]);
      const otherEmail = freshAddress("household-account");
      const otherId = await member("Maria Exempel", otherEmail, own.household_id);
      await page.getByRole("link", { name: "Hantera hushållet" }).click();
      await page.getByRole("link", { name: "Ändra uppgifter för Maria Exempel" }).click();
      await page.getByLabel("Namn", { exact: true }).fill("Maria Nytt namn");
      await page.getByLabel("Telefon", { exact: true }).fill("070-123");
      await page.getByRole("button", { name: "Spara", exact: true }).click();
      await page.getByRole("link", { name: "Ta bort Maria Nytt namn ur hushållet", exact: true }).click();
      await accessible(page);
      await page.getByRole("button", { name: "Ta bort ur hushållet", exact: true }).click();
      await expect(page).toHaveURL("/medlem");
      const [removed] = await sql<{ household_id: string | null; email: string; phone: string }>(
        "SELECT m.household_id, a.email, m.phone FROM member m JOIN account a ON a.member_id = m.id WHERE m.id = $1",
        [otherId]);
      expect(removed).toEqual({ household_id: null, email: otherEmail, phone: "070-123" });
      await linkLogin(page, "member", otherEmail);
      await expect(page.locator("main")).toContainText("Maria Nytt namn");
      await expect(page.getByRole("link", { name: "Skapa ett hushåll", exact: true })).toBeVisible();
    });

    test("only the owner edits and an owner leaving chooses a successor", async ({ page }) => {
      const ownerEmail = freshAddress("household-owner");
      const otherEmail = freshAddress("household-other");
      const ownerId = await member("Erik Ägare", ownerEmail);
      const [household] = await sql<{ id: string }>(
        "INSERT INTO household (name, owner_member_id, created_at) VALUES ('Ägt hushåll', $1, now()) RETURNING id",
        [ownerId]);
      await sql("UPDATE member SET household_id = $1 WHERE id = $2", [household.id, ownerId]);
      const otherId = await member("Maria Efterträdare", otherEmail, household.id);
      await linkLogin(page, "member", otherEmail);
      await page.getByRole("link", { name: "Hantera hushållet" }).click();
      await expect(page.getByRole("button", { name: "Spara hushållets namn" })).toHaveCount(0);
      await expect(page.getByRole("link", { name: "Lägg till en person" })).toHaveCount(0);
      await expect(page.getByRole("link", { name: "Ändra uppgifter för Erik Ägare" })).toHaveCount(0);
      await expect(page.getByRole("link", { name: "Lämna hushållet", exact: true })).toBeVisible();
      await accessible(page);
      await page.getByRole("link", { name: "Lämna hushållet", exact: true }).click();
      const csrf = await page.locator('input[name="_csrf"]').first().inputValue();
      const denied = await page.request.put("/api/member/household", {
        data: { name: "Otillåtet namn" }, headers: { "X-CSRF-TOKEN": csrf },
      });
      expect(denied.status()).toBe(403);
      await linkLogin(page, "member", ownerEmail);
      await page.getByRole("link", { name: "Hantera hushållet" }).click();
      await page.getByRole("link", { name: "Lämna hushållet", exact: true }).click();
      await accessible(page);
      await page.getByLabel("Ny ägare till hushållet").selectOption(otherId);
      await page.getByRole("button", { name: "Ta bort ur hushållet", exact: true }).click();
      await expect(page).toHaveURL("/medlem");
      const [stored] = await sql<{ owner_member_id: string }>(
        "SELECT owner_member_id FROM household WHERE id = $1", [household.id]);
      expect(stored.owner_member_id).toBe(otherId);
      await linkLogin(page, "member", otherEmail);
      await page.getByRole("link", { name: "Hantera hushållet" }).click();
      await expect(page.getByRole("button", { name: "Spara hushållets namn" })).toBeVisible();
    });

    test("an administrator changes the household owner", async ({ page }) => {
      const ownerEmail = freshAddress("admin-owner");
      const otherEmail = freshAddress("admin-successor");
      const ownerId = await member("Erik Första ägare", ownerEmail);
      const [household] = await sql<{ id: string }>(
        "INSERT INTO household (name, owner_member_id, created_at) VALUES ('Byte av ägare', $1, now()) RETURNING id",
        [ownerId]);
      await sql("UPDATE member SET household_id = $1 WHERE id = $2", [household.id, ownerId]);
      const otherId = await member("Maria Ny ägare", otherEmail, household.id);
      await linkLogin(page, "administrator", ADMINISTRATORS.ada);
      await page.goto("/admin/hushall");
      const form = page.locator(`form[action="/admin/hushall/${household.id}/agare"]`);
      await expect(form.getByLabel("Hushållets ägare")).toHaveValue(ownerId);
      await form.getByLabel("Hushållets ägare").selectOption(otherId);
      await form.getByRole("button", { name: "Spara ägare", exact: true }).click();
      await expect(page.getByRole("status")).toContainText("Hushållets ägare är ändrad.");
      await expect(form.getByLabel("Hushållets ägare")).toHaveValue(otherId);
      await accessible(page);
      await linkLogin(page, "member", ownerEmail);
      await page.getByRole("link", { name: "Hantera hushållet" }).click();
      await expect(page.getByRole("button", { name: "Spara hushållets namn" })).toHaveCount(0);
      await linkLogin(page, "member", otherEmail);
      await page.getByRole("link", { name: "Hantera hushållet" }).click();
      await expect(page.getByRole("button", { name: "Spara hushållets namn" })).toBeVisible();
    });

    test("a member leaves an existing household and creates a new one", async ({ page }) => {
      const [household] = await sql<{ id: string }>(
        "INSERT INTO household (name, created_at) VALUES ('Befintligt hushåll', now()) RETURNING id");
      const email = freshAddress("leave-household");
      const ownId = await member("Erik Exempel", email, household.id);
      const otherId = await member("Maria Exempel", freshAddress("remaining-household"), household.id);
      await linkLogin(page, "member", email);
      await page.getByRole("link", { name: "Hantera hushållet" }).click();
      await page.getByRole("link", { name: "Lämna hushållet", exact: true }).click();
      await page.getByRole("button", { name: "Ta bort ur hushållet", exact: true }).click();
      await expect(page).toHaveURL("/medlem");
      await page.getByRole("link", { name: "Skapa ett hushåll", exact: true }).click();
      await page.getByLabel("Namn, till exempel Familjen Lindqvist").fill("Eget hushåll");
      await page.getByRole("button", { name: "Skapa", exact: true }).click();
      const rows = await sql<{ id: string; household_id: string }>(
        "SELECT id, household_id FROM member WHERE id IN ($1, $2)", [ownId, otherId]);
      expect(rows.find((row) => row.id === ownId)?.household_id).not.toBe(household.id);
      expect(rows.find((row) => row.id === otherId)?.household_id).toBe(household.id);
      await expect(page.getByRole("link", { name: "Ändra uppgifter för Maria Exempel" })).toHaveCount(0);
    });
  });
}
