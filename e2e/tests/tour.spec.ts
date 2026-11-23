// The administrators' walkthrough, /js/tour.js over tag/adminTour.jte. It
// covers R011 and R018 to R025 one step at a time, and the administrator does
// each step for real, so the test does too: it adds a member, marks fees
// paid, downloads the register and prepares a mailing, and checks after every
// press that the card stands on the step the press leads to.
import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import AxeBuilder from "@axe-core/playwright";
import { expect, test, type Locator, type Page } from "@playwright/test";
import { linkLogin } from "../support/auth";
import { text } from "../support/copy";
import { clearLinkRequests, resetAdministrators, sql } from "../support/db";
import { ADMINISTRATORS, MEMBERS } from "../support/site";

/// The steps in the order the template lists them, by the name in their keys.
const STEPS = [...readFileSync(resolve(__dirname, "../../src/main/jte/tag/adminTour.jte"), "utf8")
  .matchAll(/tourStep\(title = copy\.text\("tour\.(\w+)\.title"\)/g)].map((match) => match[1]);

const MEMBER = "Rundturens Testmedlem";
const EVENT = "Rundturens evenemang";
const SUBJECT = "Rundturens utskick";

let feesBefore = 0;

async function clear(): Promise<void> {
  await sql("DELETE FROM fee WHERE id > $1", [feesBefore]);
  await sql("DELETE FROM member WHERE full_name = $1", [MEMBER]);
  await sql("DELETE FROM volunteer_shift WHERE event_title = $1", [EVENT]);
  await sql("DELETE FROM mailing WHERE subject = $1", [SUBJECT]);
}

test.beforeEach(async () => {
  await resetAdministrators();
  await clearLinkRequests();
  const [fee] = await sql<{ max: string | null }>("SELECT max(id) FROM fee");
  feesBefore = Number(fee.max ?? 0);
  await clear();
});
test.afterEach(clear);

const card = (page: Page): Locator => page.locator("section.tour");
const next = (page: Page): Promise<void> =>
  card(page).getByRole("button", { name: text("tour.next"), exact: true }).click();

/// The card stands on the step: its number, title and text, the requirement
/// it covers, and focus, so a screen reader reads it. With a target, the
/// target is outlined and the card sits just above the block that holds it.
async function expectStep(page: Page, step: string, target?: string, requirement?: string): Promise<void> {
  const number = String(STEPS.indexOf(step) + 1);
  await expect(card(page).locator("[data-tour-progress]"))
    .toHaveText(text("tour.progress", number, String(STEPS.length)));
  await expect(card(page).getByRole("heading", { level: 2 })).toHaveText(text(`tour.${step}.title`));
  await expect(card(page).locator("[data-tour-body]")).toHaveText(text(`tour.${step}.body`));
  await expect(card(page)).toBeFocused();
  await expect(card(page)).toBeInViewport();
  if (requirement) {
    await expect(card(page).locator("[data-tour-requirement]")).toHaveText(text(`tour.requirement.${requirement}`));
  } else {
    await expect(card(page).locator("[data-tour-requirement]")).toBeHidden();
  }
  if (target) {
    await expect(page.locator(".tour-target")).toHaveCount(1);
    expect(await page.locator(target).first().evaluate((element) => element.classList.contains("tour-target")),
      `${step} outlines ${target}`).toBe(true);
    expect(await card(page).evaluate((element) =>
      element.nextElementSibling?.contains(document.querySelector(".tour-target")) ?? false),
    `the card for ${step} is just above its target`).toBe(true);
  } else {
    await expect(page.locator(".tour-target")).toHaveCount(0);
  }
}

async function start(page: Page): Promise<void> {
  await linkLogin(page, "administrator", ADMINISTRATORS.ada);
  await page.getByRole("button", { name: text("tour.start"), exact: true }).click();
  await expectStep(page, "welcome");
}

test("the template lists the steps this file walks", () => {
  expect(STEPS).toEqual(["welcome", "roles", "search", "add", "addForm", "edit", "delete", "deleteConfirm",
    "unpaid", "markPaid", "householdFee", "households", "offers", "registrations", "shifts", "volunteers",
    "export", "mailing", "preview", "unsubscribe", "draft", "test", "send", "log", "finished"]);
});

/// Whether the browser holds the cookie that asks the server for the steps.
async function asksForSteps(page: Page): Promise<boolean> {
  return (await page.context().cookies()).some((cookie) => cookie.name === "rundtur");
}

test("the start button is hidden in the page as sent, and the script shows it", async ({ page }) => {
  await linkLogin(page, "administrator", ADMINISTRATORS.ada);
  const sent = await (await page.request.get("/admin")).text();
  expect(sent).toContain('<div data-tour-start="/admin" hidden>');
  await expect(page.getByRole("button", { name: text("tour.start"), exact: true })).toBeVisible();
  await expect(card(page)).toHaveCount(0);
});

test("a page holds the steps only between the start of a walkthrough and its end", async ({ page }) => {
  await linkLogin(page, "administrator", ADMINISTRATORS.ada);
  expect(await page.locator("#tour").count()).toBe(0);
  expect(await asksForSteps(page)).toBe(false);

  // The overview was sent without the steps, so starting loads it again.
  await page.getByRole("button", { name: text("tour.start"), exact: true }).click();
  await expectStep(page, "welcome");
  expect(await asksForSteps(page)).toBe(true);
  await page.goto("/admin/handlingar");
  expect(await page.locator("#tour").count()).toBe(1);

  await card(page).getByRole("button", { name: text("tour.end"), exact: true }).click();
  expect(await asksForSteps(page)).toBe(false);
  await page.goto("/admin/handlingar");
  await expect(page.locator("main h1")).toBeVisible();
  expect(await page.locator("#tour").count()).toBe(0);
});

test("the walkthrough leads through every step, each done for real", async ({ page }) => {
  const [shift] = await sql<{ id: string }>(`INSERT INTO volunteer_shift
    (event_id, event_title, event_slug, task, starts_at, ends_at, places, created_at)
    VALUES ('kulturnatten', $1, 'kulturnatten', 'GARDEROB', now() + interval '10 days',
      now() + interval '10 days 2 hours', 2, now()) RETURNING id`, [EVENT]);
  await sql(`INSERT INTO volunteer_booking (shift_id, member_id, created_at)
    SELECT $1, member_id, now() FROM account WHERE email = $2`, [shift.id, MEMBERS.erik]);
  const main = page.locator("main");
  const button = (key: string): Locator => main.getByRole("button", { name: text(key), exact: true });
  await start(page);

  // R011.
  await next(page);
  await expectStep(page, "roles", "#administratorer", "R011");

  // R018: search, add, change and remove.
  await next(page);
  await expect(page).toHaveURL("/admin/medlemmar");
  await expectStep(page, "search", "form[role=search]", "R018");
  await page.locator("#q").fill("lindqvist");
  await page.locator("#q").press("Enter");
  await expect(page).toHaveURL(/q=lindqvist/);
  await expectStep(page, "add", 'a[href="/admin/medlemmar/ny"]', "R018");
  await main.getByRole("link", { name: text("register.add"), exact: true }).click();
  await expectStep(page, "addForm", 'form[method=post][action="/admin/medlemmar"]', "R018");
  await page.locator("#fullName").fill(MEMBER);
  await button("register.add.submit").click();
  await expect(page).toHaveURL(/\/admin\/medlemmar\/\d+$/);
  await expectStep(page, "edit", "form:has(#fullName)", "R018");
  await page.locator("#phone").fill("036-12 34 56");
  await button("register.edit.submit").click();
  await expect(main.getByRole("status")).toHaveText(text("register.saved"));
  await expectStep(page, "delete", 'a[href$="/ta-bort"]', "R018");
  await main.getByRole("link", { name: text("register.delete"), exact: true }).click();
  await expectStep(page, "deleteConfirm", 'form[action$="/ta-bort"]', "R018");
  await button("register.delete.submit").click();
  await expect(page).toHaveURL("/admin/medlemmar");
  expect(await sql("SELECT 1 FROM member WHERE full_name = $1", [MEMBER])).toHaveLength(0);

  // R019: fees, for one member and for a household.
  await expectStep(page, "unpaid", "nav.choices", "R019");
  await page.locator("nav.choices a", { hasText: "Har inte betalat" }).click();
  await expect(page).toHaveURL(/avgift=obetald/);
  await expectStep(page, "markPaid", "table", "R019");
  // The table's box scrolls sideways and would cut the table's own outline off, so the box carries it.
  expect(await page.locator(".table-scroll:has(> .tour-target)").evaluate((box) => getComputedStyle(box).outlineStyle))
    .toBe("dashed");
  await page.locator('form:has(input[name=kind][value=INDIVIDUAL]) button').first().click();
  await expectStep(page, "householdFee", 'form:has(input[name=kind][value=HOUSEHOLD])', "R019");
  await page.locator('form:has(input[name=kind][value=HOUSEHOLD]) button').first().click();
  // The next step is on another page, so this one stays, now with the answer above it.
  await expect(main.getByRole("status")).toBeVisible();
  await expect(card(page).getByRole("heading", { level: 2 })).toHaveText(text("tour.householdFee.title"));
  const [fees] = await sql<{ count: string }>("SELECT count(*) FROM fee WHERE id > $1", [feesBefore]);
  expect(Number(fees.count)).toBe(2);
  await next(page);
  await expect(page).toHaveURL("/admin/hushall");
  await expectStep(page, "households", "main > h2", "R019");

  // R020: who registered for an offer and who booked a shift.
  await next(page);
  await expect(page).toHaveURL("/admin/erbjudanden");
  await expectStep(page, "offers", "table", "R020");
  await page.locator("table tbody a").first().click();
  await expectStep(page, "registrations", "#anmalda", "R020");
  await next(page);
  await expect(page).toHaveURL("/admin/volontarpass");
  await expectStep(page, "shifts", "table", "R020");
  await main.getByRole("link", { name: EVENT, exact: true }).click();
  await expectStep(page, "volunteers", "#volontarer", "R020");

  // R021.
  await next(page);
  await expect(page).toHaveURL("/admin/medlemmar");
  await expectStep(page, "export", 'a[href="/admin/medlemmar.csv"]', "R021");
  const download = page.waitForEvent("download");
  await main.getByRole("link", { name: text("register.export"), exact: true }).click();
  expect((await download).suggestedFilename()).toBe("medlemsregister.csv");

  // R022 to R025.
  await next(page);
  await expect(page).toHaveURL("/admin/utskick");
  await expectStep(page, "mailing", 'form[action="/admin/utskick/forhandsgranska"]', "R022");
  await page.locator("#audience").selectOption({ index: 1 });
  await page.locator("#subject").fill(SUBJECT);
  await page.locator("#intro").fill("Hej alla!");
  await page.locator('input[name="events"][value="kulturnatten"]').check();
  await button("adminMailings.form.preview").click();
  await expectStep(page, "preview", "iframe.mail-preview", "R023");
  await next(page);
  await expectStep(page, "unsubscribe", "#avsluta-prenumerationen", "R024");
  await next(page);
  await expectStep(page, "draft", "main > form", "R022");
  await button("adminMailings.preview.prepare").click();
  await expect(page).toHaveURL(/\/admin\/utskick\/\d+$/);
  await expectStep(page, "test", 'form[action$="/testa"]', "R023");
  await button("adminMailings.test.submit").click();
  await expect(main.getByRole("status")).toHaveText(text("adminMailings.testSent", ADMINISTRATORS.ada));
  await expectStep(page, "send", "main > h2:last-of-type", "R022");
  await next(page);
  await expect(page).toHaveURL("/admin/utskick");
  await expectStep(page, "log", "#tidigare-utskick", "R025");
  await expect(main.getByRole("link", { name: SUBJECT, exact: true })).toBeVisible();

  // The last step ends the walkthrough.
  await next(page);
  await expect(page).toHaveURL("/admin");
  await expectStep(page, "finished", "[data-tour-start]");
  await expect(card(page).getByRole("button", { name: text("tour.end"), exact: true })).toBeHidden();
  await card(page).getByRole("button", { name: text("tour.done"), exact: true }).click();
  await expect(card(page)).toHaveCount(0);
  await expect(page.locator(".tour-target")).toHaveCount(0);
  expect(await page.evaluate(() => sessionStorage.getItem("rundtur"))).toBeNull();
  expect(await asksForSteps(page)).toBe(false);
  await page.goto("/admin/medlemmar");
  await expect(card(page)).toHaveCount(0);
});

test("Nästa skips a page only an action leads to, and Föregående comes back", async ({ page }) => {
  await start(page);
  await next(page);
  await next(page);
  await expectStep(page, "search", "form[role=search]", "R018");
  await next(page);
  await expectStep(page, "add", 'a[href="/admin/medlemmar/ny"]', "R018");
  await next(page);
  await expect(page).toHaveURL("/admin/medlemmar/ny");
  await expectStep(page, "addForm", 'form[method=post][action="/admin/medlemmar"]', "R018");
  // No member was added, so there is no member page to show the next three steps on.
  await next(page);
  await expect(page).toHaveURL("/admin/medlemmar");
  await expectStep(page, "unpaid", "nav.choices", "R019");
  await card(page).getByRole("button", { name: text("tour.back"), exact: true }).click();
  await expect(page).toHaveURL("/admin/medlemmar/ny");
  await expectStep(page, "addForm", 'form[method=post][action="/admin/medlemmar"]', "R018");
});

test("a refused form keeps its step, and its message keeps focus", async ({ page }) => {
  await start(page);
  await next(page);
  await next(page);
  await next(page);
  await next(page);
  await expectStep(page, "addForm", 'form[method=post][action="/admin/medlemmar"]', "R018");
  // Past the browser's own check of the required name, to the server's.
  await page.locator('form[method=post][action="/admin/medlemmar"]').evaluate((form: HTMLFormElement) => {
    form.noValidate = true;
  });
  await page.locator("main").getByRole("button", { name: text("register.add.submit"), exact: true }).click();
  await expect(page.locator("main [role=alert]").first()).toBeVisible();
  await expect(card(page).getByRole("heading", { level: 2 })).toHaveText(text("tour.addForm.title"));
  await expect(card(page)).not.toBeFocused();
  await expect(page.locator('form[method=post][action="/admin/medlemmar"]')).toHaveClass(/tour-target/);
});

test("a page off the route says where the step is, and its button leads back", async ({ page }) => {
  await start(page);
  await next(page);
  await next(page);
  await expectStep(page, "search", "form[role=search]", "R018");
  await page.goto("/admin/handlingar");
  await expect(card(page).getByRole("heading", { level: 2 })).toHaveText(text("tour.search.title"));
  await expect(card(page).getByText(text("tour.elsewhere"), { exact: true })).toBeVisible();
  await expect(card(page).locator("[data-tour-body]")).toBeEmpty();
  await expect(card(page).locator("[data-tour-requirement]")).toBeHidden();
  await expect(card(page).getByRole("button", { name: text("tour.next"), exact: true })).toBeHidden();
  await expect(card(page).getByRole("button", { name: text("tour.back"), exact: true })).toBeHidden();
  await card(page).getByRole("button", { name: text("tour.resume"), exact: true }).click();
  await expect(page).toHaveURL("/admin/medlemmar");
  await expectStep(page, "search", "form[role=search]", "R018");
});

test("ending the walkthrough takes the card off this page and every later one", async ({ page }) => {
  await start(page);
  await next(page);
  await expectStep(page, "roles", "#administratorer", "R011");
  await card(page).getByRole("button", { name: text("tour.end"), exact: true }).click();
  await expect(card(page)).toHaveCount(0);
  await expect(page.locator(".tour-target")).toHaveCount(0);
  await expect(page.getByRole("button", { name: text("tour.start"), exact: true })).toBeFocused();
  await page.goto("/admin/medlemmar");
  await expect(page.locator("main h1")).toBeVisible();
  await expect(card(page)).toHaveCount(0);
});

test("the keyboard reaches the card's buttons first and then what the card describes", async ({ page }) => {
  await start(page);
  await next(page);
  await next(page);
  await expectStep(page, "search", "form[role=search]", "R018");
  await page.keyboard.press("Tab");
  await expect(card(page).getByRole("button", { name: text("tour.next"), exact: true })).toBeFocused();
  await page.keyboard.press("Tab");
  await expect(card(page).getByRole("button", { name: text("tour.back"), exact: true })).toBeFocused();
  await page.keyboard.press("Tab");
  await expect(card(page).getByRole("button", { name: text("tour.end"), exact: true })).toBeFocused();
  await page.keyboard.press("Tab");
  await expect(page.locator("#q")).toBeFocused();
});

test("a page with the card passes axe and stays inside a phone's width", async ({ page }) => {
  await start(page);
  await next(page);
  await next(page);
  await expectStep(page, "search", "form[role=search]", "R018");
  for (const width of [2560, 390]) {
    await page.setViewportSize({ width, height: 900 });
    const result = await new AxeBuilder({ page }).withTags(["wcag2a", "wcag2aa", "wcag21a", "wcag21aa"]).analyze();
    expect(result.violations.flatMap((violation) => violation.nodes.map((node) =>
      `${violation.id}: ${node.target.join(" ")}: ${node.failureSummary ?? violation.help}`)), `at ${width} px`)
      .toEqual([]);
    expect(await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth),
      `sideways scroll at ${width} px`).toBe(0);
  }
});
