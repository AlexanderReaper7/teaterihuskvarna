// The membership application, from the form to the welcome page.
import { expect, test, type Page } from "@playwright/test";
import { linkLogin, logOut } from "../support/auth";
import { text } from "../support/copy";
import { clearLinkRequests, expireApplication, sql } from "../support/db";
import { clearMail, mailCountAfterWait, waitForMail } from "../support/mail";
import { freshAddress, MEMBERS, PATHS } from "../support/site";

test.beforeEach(clearLinkRequests);

type Applicant = { fullName: string; email: string; phone?: string; address?: string; postalCode?: string;
  city?: string };

async function apply(page: Page, applicant: Applicant): Promise<void> {
  await page.goto("/bli-medlem");
  await page.getByLabel(text("member.name"), { exact: true }).fill(applicant.fullName);
  await page.getByLabel(text("member.email"), { exact: true }).fill(applicant.email);
  for (const [key, value] of [
    ["phone", applicant.phone], ["address", applicant.address],
    ["postalCode", applicant.postalCode], ["city", applicant.city],
  ] as const) {
    if (value !== undefined) {
      await page.getByLabel(`${text(`member.${key}`)} ${text("form.optional")}`, { exact: true }).fill(value);
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

/// A link that would confirm nothing says so when it is opened, and shows no button to press.
async function opensExpired(page: Page, link: string): Promise<void> {
  await page.goto(link);
  await expect(expired(page)).toBeVisible();
  await expect(page.locator("main form")).toHaveCount(0);
}

test.describe("what works", () => {
  test("a visitor applies, confirms, and the welcome page's Logga in logs them in without a mail", async ({ page }) => {
    // #47: the browser that sent the form is the one the login works in, as
    // for a login link. docs/decisions/0015, "Logging in from the welcome page".
    const email = freshAddress("sokande");
    const link = await applyForLink(page, {
      fullName: "Åsa Öberg", email, phone: "070-123 45 67", address: "Storgatan 1", postalCode: "561 31",
      city: "Huskvarna",
    });
    expect(link).toMatch(/^http:\/\/localhost:55556\/bli-medlem\/bekrafta\?token=/);

    await confirm(page, link);
    await expect(page.getByRole("heading", { name: text("application.welcome.heading", "Åsa Öberg") }))
      .toBeVisible();
    await expect(page.getByText(text("application.welcome.payment", "50 kr", "100 kr", "123-4567", email)))
      .toBeVisible();
    await expect(page.getByText(text("application.welcome.login", email))).toBeVisible();

    await page.getByRole("link", { name: text("application.welcome.loginLink") }).click();
    await expect(page).toHaveURL(PATHS.member.home);
    await expect(page.getByText("Åsa Öberg", { exact: true })).toBeVisible();
    // Only the confirmation mail: the login needed none.
    expect(await mailCountAfterWait(email)).toBe(1);
  });

  test("a member who logged in from the welcome page can log in again later by link", async ({ page }) => {
    const email = freshAddress("senare");
    await confirm(page, await applyForLink(page, { fullName: "Senare Inloggning", email }));
    await page.getByRole("link", { name: text("application.welcome.loginLink") }).click();
    await expect(page).toHaveURL(PATHS.member.home);
    await logOut(page, "member");

    await linkLogin(page, "member", email);
    await expect(page.getByText("Senare Inloggning", { exact: true })).toBeVisible();
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

  test("the confirmation link opened a second time shows the expired page", async ({ page }) => {
    const link = await applyForLink(page, { fullName: "Två Gånger", email: freshAddress("tva") });
    await confirm(page, link);
    await expect(page.getByRole("heading", { name: text("application.welcome.heading", "Två Gånger") }))
      .toBeVisible();
    await opensExpired(page, link);
  });

  test("a confirm page left open while the link was used shows the expired page when its button is pressed",
    async ({ page, context }) => {
      const link = await applyForLink(page, { fullName: "Två Flikar", email: freshAddress("flikar") });
      const other = await context.newPage();
      await other.goto(link);
      await expect(other.getByRole("button", { name: text("application.confirm.submit") })).toBeVisible();
      await confirm(page, link);
      await expect(page.getByRole("heading", { name: text("application.welcome.heading", "Två Flikar") }))
        .toBeVisible();
      await other.getByRole("button", { name: text("application.confirm.submit") }).click();
      await expect(expired(other)).toBeVisible();
    });

  test("an expired application shows the expired page and makes no member", async ({ page }) => {
    const email = freshAddress("gammal");
    const link = await applyForLink(page, { fullName: "För Sen", email });
    await expireApplication(email);
    await opensExpired(page, link);
    expect(await sql("SELECT 1 FROM account WHERE lower(email) = lower($1)", [email])).toHaveLength(0);
  });

  test("applying twice: only the newest link works", async ({ page }) => {
    const email = freshAddress("dubbel");
    const first = await applyForLink(page, { fullName: "Första Försöket", email });
    const second = await applyForLink(page, { fullName: "Andra Försöket", email });
    await opensExpired(page, first);
    await confirm(page, second);
    await expect(page.getByRole("heading", { name: text("application.welcome.heading", "Andra Försöket") }))
      .toBeVisible();
  });

  test("a confirmation link opened in another browser confirms there, and its Logga in asks for the address",
    async ({ page, browser }) => {
      // Someone who applies with their own address and passes the link on must
      // not get the other person logged in to the new account, the login CSRF
      // that binding login links to a browser closed: docs/decisions/0015.
      const email = freshAddress("vidare");
      const link = await applyForLink(page, { fullName: "Vidare Skickad", email });
      const other = await browser.newContext();
      const otherPage = await other.newPage();
      await confirm(otherPage, link);
      await expect(otherPage.getByRole("heading", { name: text("application.welcome.heading", "Vidare Skickad") }))
        .toBeVisible();

      await otherPage.getByRole("link", { name: text("application.welcome.loginLink") }).click();
      await expect(otherPage).toHaveURL(PATHS.member.login);
      await expect(otherPage.locator("#email")).toBeVisible();
      await expect(otherPage.getByRole("alert").filter({ hasText: text("login.failed") })).toHaveCount(0);
      await otherPage.goto(PATHS.member.home);
      await expect(otherPage).toHaveURL(PATHS.member.login);
      await other.close();
    });

  test("the welcome page's Logga in does not log in once the browser's login cookie is gone", async ({
    page, context,
  }) => {
    // The cookie lasts as long as a login link, an hour from the form, so
    // confirming later in the same browser confirms without logging in.
    const email = freshAddress("utgangen");
    const link = await applyForLink(page, { fullName: "Utgången Kaka", email });
    await context.clearCookies({ name: "login-browser" });
    await confirm(page, link);
    await page.getByRole("link", { name: text("application.welcome.loginLink") }).click();
    await expect(page).toHaveURL(PATHS.member.login);
  });

  test("a member who applies again gets a login link that works only in the browser they applied in", async ({
    page, browser,
  }) => {
    // 0015, "Bli medlem from an address that already has an account": the
    // form set the login cookie, and the link is bound to it, as a link from
    // the login page is. It has no code, so another device gets the login page.
    await clearMail(MEMBERS.erik);
    await apply(page, { fullName: "Erik Lindqvist", email: "Erik.Lindqvist@Example.test" });
    await expect(page).toHaveURL("/bli-medlem/skickat");
    const mail = await waitForMail(MEMBERS.erik);
    expect(mail.subject).toBe(text("application.mail.member.subject"));
    expect(mail.link).toMatch(/^http:\/\/localhost:55556\/logga-in\/lank\?token=/);
    expect(mail.text.split(/\r?\n/)).toContain(`http://localhost:55556${PATHS.member.login}`);
    expect(mail.code).toBeUndefined();

    const other = await browser.newContext();
    const otherPage = await other.newPage();
    await otherPage.goto(mail.link);
    await expect(otherPage).toHaveURL(PATHS.member.login);
    await expect(otherPage.locator("#email")).toBeVisible();
    await other.close();

    await page.goto(mail.link);
    await expect(page).toHaveURL(PATHS.member.home);
    await expect(page.getByText("Erik Lindqvist", { exact: true })).toBeVisible();
  });

  test("a new member who applies once more after confirming gets the mail for a member", async ({ page }) => {
    const email = freshAddress("igen");
    await confirm(page, await applyForLink(page, { fullName: "Redan Medlem", email }));
    await clearMail(email);
    await apply(page, { fullName: "Redan Medlem", email });
    expect((await waitForMail(email)).subject).toBe(text("application.mail.member.subject"));
  });

  test("a mangled confirmation link shows the expired page", async ({ page }) => {
    const link = new URL(await applyForLink(page, { fullName: "Trasig Länk", email: freshAddress("trasig") }));
    await opensExpired(page, `${link.pathname}?token=${link.searchParams.get("token")!.slice(0, 12)}`);
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
