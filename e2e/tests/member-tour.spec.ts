// The members' walkthrough of Mina sidor, /js/tour.js over tag/memberTour.jte.
// The member does what a step asks for on the real page, so the test does
// too: it changes a telephone number, and checks after every press that the
// card stands on the step the press leads to. What the card looks like and
// how it keeps off its target is the same script as the administrators', and
// tests/tour.spec.ts has the rest of it.
import { expect, test, type Page } from "@playwright/test";
import { linkLogin } from "../support/auth";
import { text } from "../support/copy";
import { clearLinkRequests, sql } from "../support/db";
import { MEMBERS } from "../support/site";
import { card, expectOverlay, expectTourStep, next, tourSteps } from "../support/tour";

const STEPS = tourSteps("memberTour.jte", "memberTour");
const TOUR = { steps: STEPS, keys: "memberTour" };
const expectStep = (page: Page, step: string, target?: string): Promise<void> =>
  expectTourStep(page, TOUR, step, target);

/// Anders has no household and has not paid, so his pages show the most to do.
const PHONE = "070-174 06 05";

async function restore(): Promise<void> {
  await sql("UPDATE member SET phone = $1 WHERE id = (SELECT member_id FROM account WHERE email = $2)",
    [PHONE, MEMBERS.anders]);
}

test.beforeEach(async () => {
  await clearLinkRequests();
  await restore();
});
test.afterEach(restore);

async function start(page: Page): Promise<void> {
  await linkLogin(page, "member", MEMBERS.anders);
  await page.getByRole("button", { name: text("tour.start"), exact: true }).click();
  await expectStep(page, "welcome");
}

/// The cookies that ask the server for the steps, by the paths they are sent to.
async function asksForSteps(page: Page): Promise<string[]> {
  return (await page.context().cookies()).filter((cookie) => cookie.name === "rundtur").map((cookie) => cookie.path);
}

test("the template lists the steps this file walks", () => {
  expect(STEPS).toEqual(["welcome", "fee", "details", "contact", "household", "householdPage", "offers", "offer",
    "documents", "shifts", "passkeys", "devices", "finished"]);
});

test("the start button is hidden in the page as sent, and a page holds no step before a walkthrough starts",
  async ({ page }) => {
    await linkLogin(page, "member", MEMBERS.anders);
    const sent = await (await page.request.get("/medlem")).text();
    expect(sent).toContain('<div data-tour-start="/medlem" hidden>');
    expect(sent).not.toContain("data-tour-step");
    await expect(page.getByRole("button", { name: text("tour.start"), exact: true })).toBeVisible();
    await expect(card(page)).toHaveCount(0);
  });

test("the walkthrough leads through every step of Mina sidor", async ({ page }) => {
  const main = page.locator("main");
  await start(page);
  // The cookie goes to the member pages only, so no administrator page is sent these steps.
  expect(await asksForSteps(page)).toEqual(["/medlem"]);

  await next(page);
  await expectStep(page, "fee", "#avgift");
  // A heading stands for its whole section, which a frame goes around.
  await expect(page.locator(".tour-frame")).toHaveCount(1);

  // The link under the heading leads to the form, and saving the form leads back.
  await next(page);
  await expectStep(page, "details", "#kontaktuppgifter");
  await main.getByRole("link", { name: text("member.contact.edit"), exact: true }).click();
  await expect(page).toHaveURL("/medlem/kontaktuppgifter");
  await expectStep(page, "contact", 'form[action="/medlem/kontaktuppgifter"]');
  await page.locator("#phone").fill("036-12 34 56");
  await main.getByRole("button", { name: text("member.contact.submit"), exact: true }).click();
  await expect(page).toHaveURL("/medlem");
  await expect(main.getByRole("status")).toHaveText(text("member.contact.saved"));
  await expectStep(page, "household", "#hushall");
  await expect(main.locator("dl.details")).toContainText("036-12 34 56");

  await main.getByRole("link", { name: text("household.create"), exact: true }).click();
  await expect(page).toHaveURL("/medlem/hushall");
  await expectStep(page, "householdPage", "main > h1");

  await next(page);
  await expect(page).toHaveURL("/medlem/erbjudanden");
  await expectStep(page, "offers", "main > h1");
  await main.locator("h2 a").first().click();
  await expect(page).toHaveURL(/\/medlem\/erbjudanden\/\d+$/);
  await expectStep(page, "offer", "main > h1");

  await next(page);
  await expect(page).toHaveURL("/medlem/handlingar");
  await expectStep(page, "documents", "main > h1");
  await next(page);
  await expect(page).toHaveURL("/medlem/volontar");
  await expectStep(page, "shifts", "main > h1");

  await next(page);
  await expect(page).toHaveURL("/medlem");
  await expectStep(page, "passkeys", "[data-passkeys] h2");
  await next(page);
  await expectStep(page, "devices", "[data-devices] h2");

  // The last step ends the walkthrough.
  await next(page);
  await expectStep(page, "finished", "[data-tour-start]");
  await card(page).getByRole("button", { name: text("tour.done"), exact: true }).click();
  await expect(card(page)).toHaveCount(0);
  await expect(page.locator(".tour-target, .tour-frame")).toHaveCount(0);
  expect(await page.evaluate(() => sessionStorage.getItem("rundtur:/medlem"))).toBeNull();
  expect(await asksForSteps(page)).toEqual([]);
  await page.goto("/medlem/erbjudanden");
  await expect(page.locator("main h1")).toBeVisible();
  await expect(card(page)).toHaveCount(0);
  expect(await page.locator("#tour").count()).toBe(0);
});

test("Nästa alone reaches the last step, past the page of an offer nobody opened", async ({ page }) => {
  await start(page);
  const progress = card(page).locator("[data-tour-progress]");
  const seen: string[] = [];
  for (let presses = 0; presses < STEPS.length; presses++) {
    const at = (await progress.innerText()).trim();
    seen.push(at);
    if (at === text("tour.progress", String(STEPS.length), String(STEPS.length))) {
      break;
    }
    await next(page);
    await expect(progress).not.toHaveText(at);
  }
  const numbers = seen.map((at) => Number(/\d+/.exec(at)?.[0]));
  expect(numbers).toEqual(STEPS.map((_, index) => index + 1).filter((number) => number !== STEPS.indexOf("offer") + 1));
});

for (const [width, height] of [[1280, 720], [390, 844]] as const) {
  test(`at ${width} by ${height} px the card lies on no step's target`, async ({ page }) => {
    await start(page);
    await page.setViewportSize({ width, height });
    const progress = card(page).locator("[data-tour-progress]");
    const last = text("tour.progress", String(STEPS.length), String(STEPS.length));
    for (let presses = 0; presses < STEPS.length; presses++) {
      const at = (await progress.innerText()).trim();
      await expect(card(page)).toBeFocused();
      await expectOverlay(page, at);
      expect(await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth),
        `sideways scroll on ${at}`).toBe(0);
      if (at === last) {
        return;
      }
      await next(page);
      await expect(progress).not.toHaveText(at);
    }
    throw new Error("the last step was never reached");
  });
}
