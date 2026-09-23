// The membership application, from the form to the welcome page.
import { expect, test, type Page } from "@playwright/test";
import { linkLogin } from "../support/auth";
import { text } from "../support/copy";
import { clearLinkRequests, expireApplication, sql } from "../support/db";
import { clearMail, waitForMail } from "../support/mail";
import { freshAddress, MEMBERS, PATHS } from "../support/site";

test.beforeEach(clearLinkRequests);

type Applicant = { fullName: string; email: string; phone?: string; address?: string; postalCode?: string;
  city?: string };

async function apply(page: Page, applicant: Applicant): Promise<void> {
  await page.goto("/bli-medlem");
  await page.getByLabel(text("application.form.fullName"), { exact: true }).fill(applicant.fullName);
  await page.getByLabel(text("application.form.email"), { exact: true }).fill(applicant.email);
  for (const [key, value] of [
    ["phone", applicant.phone], ["address", applicant.address],
    ["postalCode", applicant.postalCode], ["city", applicant.city],
  ] as const) {
    if (value !== undefined) {
      await page.getByLabel(text(`application.form.${key}`), { exact: true }).fill(value);
    }
  }
  await page.getByRole("button", { name: text("application.submit") }).click();
}

/// Applies and returns the mailed confirmation link.
async function applyForLink(page: Page, applicant: Applicant): Promise<string> {
  await clearMail(applicant.email);
  await apply(page, applicant);
  await expect(page).toHaveURL("/bli-medlem/skickat");
  const mail = await waitForMail(applicant.email);
  expect(mail.subject).toBe(text("application.mail.subject"));
  return mail.link;
}

async function confirm(page: Page, link: string): Promise<void> {
  await page.goto(link);
  await expect(page.getByRole("heading", { name: text("application.confirm.heading") })).toBeVisible();
  await page.getByRole("button", { name: text("application.confirm.submit") }).click();
}

const expired = (page: Page) => page.getByRole("heading", { name: text("application.expired.heading") });

test.describe("what works", () => {
  test("a visitor applies, confirms, and can then log in as a member", async ({ page }) => {
    const email = freshAddress("sokande");
    const link = await applyForLink(page, {
      fullName: "Åsa Öberg", email, phone: "070-123 45 67", address: "Storgatan 1", postalCode: "561 31",
      city: "Huskvarna",
    });
    expect(link).toMatch(/^http:\/\/localhost:55556\/bli-medlem\/bekrafta\?token=/);

    await confirm(page, link);
    await expect(page.getByRole("heading", { name: text("application.welcome.heading", "Åsa Öberg") }))
      .toBeVisible();
    await expect(page.getByText(text("application.welcome.payment", "123-4567"))).toBeVisible();
    await expect(page.getByText(text("application.welcome.login", email))).toBeVisible();

    await linkLogin(page, "member", email);
    await expect(page.getByText("Åsa Öberg")).toBeVisible();
  });

  test("opening the confirmation link does not confirm by itself", async ({ page }) => {
    // Mail scanners open every link. Only the button's POST may use it up.
    const email = freshAddress("skanner");
    const link = await applyForLink(page, { fullName: "Skannad Person", email });
    await page.request.get(link);
    await page.request.get(link);
    await confirm(page, link);
    await expect(page.getByRole("heading", { name: text("application.welcome.heading", "Skannad Person") }))
      .toBeVisible();
  });
});

test.describe("what a person might get wrong", () => {
  test("a name over 100 characters shows the error beside the field and keeps what was typed", async ({ page }) => {
    const email = freshAddress("langt");
    await apply(page, { fullName: "A".repeat(101), email, city: "Jönköping" });
    await expect(page).toHaveURL("/bli-medlem");
    await expect(page.locator("#fullName-error")).toHaveText(text("application.fullName.size"));
    await expect(page.locator("#fullName")).toHaveAttribute("aria-invalid", "true");
    await expect(page.locator("#email")).toHaveValue(email);
    await expect(page.locator("#city")).toHaveValue("Jönköping");
    await expect(page.getByRole("alert")).toContainText(text("form.errors"));
  });

  test("a name of only spaces is refused", async ({ page }) => {
    await apply(page, { fullName: "   ", email: freshAddress("mellanslag") });
    await expect(page.locator("#fullName-error")).toHaveText(text("application.fullName.required"));
  });

  test("every optional field too long at once lists every error", async ({ page }) => {
    await apply(page, {
      fullName: "För Långt", email: freshAddress("allt"), phone: "1".repeat(33), address: "g".repeat(201),
      postalCode: "1".repeat(11), city: "o".repeat(101),
    });
    await expect(page.locator("#phone-error")).toHaveText(text("application.phone.size"));
    await expect(page.locator("#address-error")).toHaveText(text("application.address.size"));
    await expect(page.locator("#postalCode-error")).toHaveText(text("application.postalCode.size"));
    await expect(page.locator("#city-error")).toHaveText(text("application.city.size"));
  });

  test("a name with markup is shown as text on the welcome page", async ({ page }) => {
    const name = `<img src=x onerror="window.injected=1">`;
    await confirm(page, await applyForLink(page, { fullName: name, email: freshAddress("markup") }));
    await expect(page.getByRole("heading", { name: text("application.welcome.heading", name) })).toBeVisible();
    expect(await page.evaluate(() => (window as { injected?: number }).injected)).toBeUndefined();
  });

  test("the confirmation link used a second time shows the expired page", async ({ page }) => {
    const link = await applyForLink(page, { fullName: "Två Gånger", email: freshAddress("tva") });
    await confirm(page, link);
    await expect(page.getByRole("heading", { name: text("application.welcome.heading", "Två Gånger") }))
      .toBeVisible();
    await confirm(page, link);
    await expect(expired(page)).toBeVisible();
  });

  test("an expired application shows the expired page and makes no member", async ({ page }) => {
    const email = freshAddress("gammal");
    const link = await applyForLink(page, { fullName: "För Sen", email });
    await expireApplication(email);
    await confirm(page, link);
    await expect(expired(page)).toBeVisible();
    expect(await sql("SELECT 1 FROM account WHERE lower(email) = lower($1)", [email])).toHaveLength(0);
  });

  test("applying twice: only the newest link works", async ({ page }) => {
    const email = freshAddress("dubbel");
    const first = await applyForLink(page, { fullName: "Första Försöket", email });
    const second = await applyForLink(page, { fullName: "Andra Försöket", email });
    await confirm(page, first);
    await expect(expired(page)).toBeVisible();
    await confirm(page, second);
    await expect(page.getByRole("heading", { name: text("application.welcome.heading", "Andra Försöket") }))
      .toBeVisible();
  });

  test("a member who applies again gets a login link instead", async ({ page }) => {
    await clearMail(MEMBERS.erik);
    await apply(page, { fullName: "Erik Lindqvist", email: "Erik.Lindqvist@Example.test" });
    await expect(page).toHaveURL("/bli-medlem/skickat");
    const mail = await waitForMail(MEMBERS.erik);
    expect(mail.subject).toBe(text("login.mail.member.subject"));
    await page.goto(mail.link);
    await expect(page).toHaveURL(PATHS.member.home);
  });

  test("a new member who applies once more after confirming gets a login link", async ({ page }) => {
    const email = freshAddress("igen");
    await confirm(page, await applyForLink(page, { fullName: "Redan Medlem", email }));
    await clearMail(email);
    await apply(page, { fullName: "Redan Medlem", email });
    expect((await waitForMail(email)).subject).toBe(text("login.mail.member.subject"));
  });

  test("a mangled confirmation link shows the expired page", async ({ page }) => {
    const link = new URL(await applyForLink(page, { fullName: "Trasig Länk", email: freshAddress("trasig") }));
    await confirm(page, `${link.pathname}?token=${link.searchParams.get("token")!.slice(0, 12)}`);
    await expect(expired(page)).toBeVisible();
  });

  test("the confirmation page without a token shows the expired page", async ({ page }) => {
    await page.goto("/bli-medlem/bekrafta");
    await expect(expired(page)).toBeVisible();
  });

  test("pressing Bekräfta twice still ends on the welcome page", async ({ page }) => {
    // Firefox sent both presses before forms.js, and showed the second answer,
    // the expired page, although the first made the person a member.
    const link = await applyForLink(page, { fullName: "Dubbel Klick", email: freshAddress("dubbelklick") });
    await page.goto(link);
    await page.getByRole("button", { name: text("application.confirm.submit") }).dblclick();
    await expect(page.getByRole("heading", { name: text("application.welcome.heading", "Dubbel Klick") }))
      .toBeVisible();
  });
});
