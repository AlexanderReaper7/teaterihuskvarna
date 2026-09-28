// R006: every page, scanned by axe-core for the WCAG 2.1 A and AA rules it can
// check by machine. axe finds roughly a third of WCAG failures; keyboard use,
// reading order and whether a text alternative says the right thing still need
// a person.
import AxeBuilder from "@axe-core/playwright";
import { expect, test, type Page } from "@playwright/test";
import { linkLogin } from "../support/auth";
import { clearLinkRequests, resetAdministrators } from "../support/db";
import { ADMINISTRATORS, MEMBERS } from "../support/site";

test.beforeEach(async () => {
  await resetAdministrators();
  await clearLinkRequests();
});

/// Every violation on the page, one line each, so a failure names the rule
/// and the element without opening the report.
async function violations(page: Page): Promise<string[]> {
  const result = await new AxeBuilder({ page })
    .withTags(["wcag2a", "wcag2aa", "wcag21a", "wcag21aa"])
    .analyze();
  return result.violations.flatMap((v) =>
    v.nodes.map((n) => `${v.id} (${v.impact}): ${n.target.join(" ")}: ${n.failureSummary ?? v.help}`));
}

async function scan(page: Page, path: string): Promise<void> {
  await page.goto(path);
  expect(await violations(page), path).toEqual([]);
}

/// The first link on `list` whose address starts with `prefix` and continues
/// with a number, for pages that need an id from the seed data.
async function firstDetail(page: Page, list: string, prefix: string): Promise<string> {
  await page.goto(list);
  const href = await page.locator(`a[href^="${prefix}"]`).evaluateAll(
    (links, p) => links.map((a) => a.getAttribute("href") ?? "")
      .find((h) => new RegExp(`^${p}\\d+$`).test(h)) ?? "",
    prefix);
  expect(href, `a link to ${prefix}<id> on ${list}`).not.toBe("");
  return href;
}

// Proves the scan can fail, so a green run means the pages passed rather than
// that axe never ran.
test("the scan reports an image without a text alternative", async ({ page }) => {
  await page.setContent('<html lang="sv"><title>t</title><main><img src="data:,"></main></html>');
  expect(await violations(page)).toEqual([expect.stringMatching(/^image-alt \(critical\)/)]);
});

const PUBLIC = [
  "/start",
  "/kalender",
  "/evenemang/kulturnatten",
  "/nyheter",
  "/nyheter/ny-webbplats",
  "/om-foreningen",
  "/styrelsen",
  "/produktioner",
  "/ludde-priser",
  "/kontakt",
  "/partners",
  "/bli-medlem",
  "/logga-in",
  "/admin/logga-in",
  "/finns-inte",
];

for (const path of PUBLIC) {
  test(`${path} passes axe`, async ({ page }) => {
    await scan(page, path);
  });
}

test("the member pages pass axe", async ({ page }) => {
  await linkLogin(page, "member", MEMBERS.erik);
  for (const path of ["/medlem", "/medlem/kontaktuppgifter", "/medlem/erbjudanden", "/medlem/handlingar",
    "/medlem/volontar"]) {
    await scan(page, path);
  }
  await scan(page, await firstDetail(page, "/medlem/erbjudanden", "/medlem/erbjudanden/"));
});

test("the administrator pages pass axe", async ({ page }) => {
  await linkLogin(page, "administrator", ADMINISTRATORS.ada);
  for (const path of ["/admin", "/admin/medlemmar", "/admin/medlemmar/ny", "/admin/hushall",
    "/admin/erbjudanden", "/admin/erbjudanden/nytt", "/admin/handlingar", "/admin/volontarpass",
    "/admin/utskick"]) {
    await scan(page, path);
  }
  await scan(page, await firstDetail(page, "/admin/medlemmar", "/admin/medlemmar/"));
  await scan(page, await firstDetail(page, "/admin/erbjudanden", "/admin/erbjudanden/"));
});
