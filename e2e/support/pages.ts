import { expect, type Page } from "@playwright/test";
import { sql } from "./db";
import { text } from "./copy";
import { PATHS } from "./site";

export const PUBLIC = ["/", "/kalender", "/evenemang/kulturnatten", "/nyheter", "/nyheter/ny-webbplats",
  "/om-foreningen", "/styrelsen", "/produktioner", "/ludde-priser", "/kontakt", "/partners", "/bli-medlem",
  "/bli-medlem/skickat", "/bli-medlem/bekrafta", "/bli-medlem/bekrafta?token=page-check", "/inbjudan",
  "/inbjudan?token=page-check", PATHS.member.login, PATHS.member.sent, PATHS.administrator.login, PATHS.administrator.sent, "/finns-inte"];
export const MEMBER = ["/medlem", "/medlem/kontaktuppgifter", "/medlem/erbjudanden", "/medlem/handlingar",
  "/medlem/volontar", "/medlem/hushall"];
export const ADMIN = ["/admin", "/admin/medlemmar", "/admin/medlemmar/ny", "/admin/hushall", "/admin/erbjudanden",
  "/admin/erbjudanden/nytt", "/admin/handlingar", "/admin/volontarpass", "/admin/utskick"];

/// A missing page or a redirect to login must fail before its layout is checked.
export async function visit(page: Page, path: string): Promise<void> {
  const response = await page.goto(path);
  expect(response?.status(), path).toBe(path === "/finns-inte" ? 404 : 200);
  expect(new URL(page.url()).pathname, path).toBe(new URL(path, page.url()).pathname);
  await expect(page.locator("main h1")).toBeVisible();
  await page.evaluate(() => document.fonts.ready);
}

export async function scan(page: Page, paths: string[], check: (page: Page) => Promise<string[]>,
  shown: Record<string, string> = {}): Promise<string[]> {
  const found: string[] = [];
  for (const path of [...new Set(paths)]) {
    await visit(page, path);
    if (shown[path]) {
      await expect(page.locator(shown[path]).first()).toBeVisible();
    }
    found.push(...(await check(page)).map((problem) => `${path}: ${problem}`));
  }
  return found;
}

/// At least one detail page must exist; callers create shifts and mailings first.
export async function details(page: Page, list: string, prefix: string): Promise<string[]> {
  await visit(page, list);
  const hrefs = await page.locator(`a[href^="${prefix}"]`).evaluateAll(
    (links, p) => links.map((a) => a.getAttribute("href") ?? "").filter((h) => new RegExp(`^${p}\\d+$`).test(h)),
    prefix);
  const paths = [...new Set(hrefs)].slice(0, 2);
  expect(paths, `detail pages on ${list}`).not.toEqual([]);
  return paths;
}

/// Own test records make owner-only forms reachable without changing seeded households.
export async function householdPages(): Promise<{ email: string; paths: string[] }> {
  const email = "page-owner@example.test";
  let [owner] = await sql<{ id: string }>("SELECT member_id AS id FROM account WHERE email = $1", [email]);
  if (!owner) {
    const [household] = await sql<{ id: string }>(
      "INSERT INTO household (name, created_at) VALUES ('Familjen Sidtest', now()) RETURNING id");
    [owner] = await sql<{ id: string }>(
      "INSERT INTO member (full_name, household_id, created_at) VALUES ('Erik Sidtest', $1, now()) RETURNING id",
      [household.id]);
    await sql("INSERT INTO account (member_id, email, created_at) VALUES ($1, $2, now())", [owner.id, email]);
    await sql("UPDATE household SET owner_member_id = $1 WHERE id = $2", [owner.id, household.id]);
    const [other] = await sql<{ id: string }>(
      "INSERT INTO member (full_name, household_id, created_at) VALUES ('Maria Sidtest', $1, now()) RETURNING id",
      [household.id]);
    await sql("INSERT INTO account (member_id, email, created_at) VALUES ($1, 'page-member@example.test', now())",
      [other.id]);
    await sql("INSERT INTO member (full_name, household_id, created_at) VALUES ('Olle Sidtest', $1, now())",
      [household.id]);
  }
  const [other] = await sql<{ id: string }>(
    "SELECT m.id FROM member m JOIN member owner ON owner.household_id = m.household_id WHERE owner.id = $1 AND m.id <> owner.id ORDER BY m.id LIMIT 1",
    [owner.id]);
  expect(other, "another household member").toBeDefined();
  const base = "/medlem/hushall/medlemmar/";
  return { email, paths: ["/medlem/hushall/ny", base + other.id, base + other.id + "/ta-bort",
    base + owner.id + "/ta-bort"] };
}

/// A real draft in the fake Brevo and a booked shift exist even in a standalone run.
/// The preview is left on screen so the caller can check that POST-only page too.
export async function prepareAdministratorPages(page: Page): Promise<void> {
  await sql(`INSERT INTO volunteer_shift
    (event_id, event_title, event_slug, task, starts_at, ends_at, places, created_at)
    SELECT 'kulturnatten', 'Sidtestets volontärpass', 'kulturnatten', 'GARDEROB',
      now() + interval '10 days', now() + interval '10 days 2 hours', 2, now()
    WHERE NOT EXISTS (SELECT 1 FROM volunteer_shift WHERE event_title = 'Sidtestets volontärpass')`);
  await sql(`INSERT INTO volunteer_booking (shift_id, member_id, created_at)
    SELECT s.id, a.member_id, now() FROM volunteer_shift s CROSS JOIN account a
    WHERE s.event_title = 'Sidtestets volontärpass' AND a.email = 'erik.lindqvist@example.test'
    ON CONFLICT (shift_id, member_id) DO NOTHING`);
  await visit(page, "/admin/utskick");
  await page.locator("#audience").selectOption({ index: 1 });
  await page.locator("#subject").fill("Sidtestets utskick");
  await page.locator('input[name="events"]').first().check();
  await page.getByRole("button", { name: text("adminMailings.form.preview") }).click();
  await expect(page.getByRole("button", { name: text("adminMailings.preview.prepare") })).toBeVisible();
}

export async function saveMailingDraft(page: Page): Promise<void> {
  await page.getByRole("button", { name: text("adminMailings.preview.prepare") }).click();
  await expect(page).toHaveURL(/\/admin\/utskick\/\d+$/);
  await expect(page.locator("main h1")).toBeVisible();
}
