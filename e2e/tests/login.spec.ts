// Login by link for members and administrators: docs/decisions/0015.
import { expect, test, type Page } from "@playwright/test";
import { linkLogin, logOut, mailedLink, requestLink } from "../support/auth";
import { text } from "../support/copy";
import { clearLinkRequests, expireLoginLinks, resetAdministrators } from "../support/db";
import { clearMail, mailCountAfterWait, waitForMail } from "../support/mail";
import { ADMINISTRATORS, MEMBERS, PATHS, SITE } from "../support/site";

test.beforeEach(async () => {
  await resetAdministrators();
  await clearLinkRequests();
});

/// The message a failed link leaves on the login page. Scoped to its text,
/// because the passkey section has an alert of its own.
const linkFailed = (page: Page) => page.getByRole("alert").filter({ hasText: text("login.failed") });

test.describe("what works", () => {
  test("a member logs in with the mailed link and sees their own details", async ({ page }) => {
    await clearMail(MEMBERS.erik);
    await requestLink(page, "member", MEMBERS.erik);
    await expect(page.getByRole("heading", { name: text("login.sent.heading") })).toBeVisible();
    const mail = await waitForMail(MEMBERS.erik);
    expect(mail.subject).toBe(text("login.mail.member.subject"));
    expect(mail.link).toMatch(/^http:\/\/localhost:55556\/logga-in\/lank\?token=/);

    await page.goto(mail.link);
    await expect(page).toHaveURL(PATHS.member.home);
    await expect(page.getByRole("heading", { name: text("member.heading") })).toBeVisible();
    await expect(page.getByText("Erik Lindqvist", { exact: true })).toBeVisible();
    await expect(page.getByText(MEMBERS.erik)).toBeVisible();
  });

  test("an administrator logs in with the mailed link", async ({ page }) => {
    await clearMail(ADMINISTRATORS.gunnar);
    await requestLink(page, "administrator", ADMINISTRATORS.gunnar);
    const mail = await waitForMail(ADMINISTRATORS.gunnar);
    expect(mail.subject).toBe(text("login.mail.administrator.subject"));
    expect(mail.link).toMatch(/^http:\/\/localhost:55556\/admin\/logga-in\/lank\?token=/);

    await page.goto(mail.link);
    await expect(page).toHaveURL(PATHS.administrator.home);
    await expect(page.getByText(text("admin.signedInAs", "Gunnar Wik"))).toBeVisible();
  });

  test("logging out ends the session", async ({ page }) => {
    await linkLogin(page, "member", MEMBERS.maria);
    await logOut(page, "member");
    await page.goto(PATHS.member.home);
    await expect(page).toHaveURL(PATHS.member.login);
  });

  test("the link page logs in without JavaScript when the button is pressed", async ({ browser }) => {
    const context = await browser.newContext({ javaScriptEnabled: false });
    const page = await context.newPage();
    const link = await mailedLink(page, "member", MEMBERS.johan);
    await page.goto(link);
    // Without the script the page waits for the person.
    await expect(page).toHaveURL(/\/logga-in\/lank\?token=/);
    await page.getByRole("button", { name: text("login.link.submit") }).click();
    await expect(page).toHaveURL(PATHS.member.home);
    await context.close();
  });

  test("a link opened in another browser says to use the code, which logs in where it was asked for", async ({
    page,
    browser,
  }) => {
    // 0015, "A link works only in the browser that asked for it".
    await clearLinkRequests();
    await clearMail(MEMBERS.sara);
    await requestLink(page, "member", MEMBERS.sara);
    const mail = await waitForMail(MEMBERS.sara);
    expect(mail.text).toContain("Ge aldrig koden till någon. Föreningen frågar aldrig efter den.");
    const phone = await browser.newContext();
    const phonePage = await phone.newPage();
    await phonePage.goto(mail.link);
    await expect(phonePage.getByRole("heading", { name: text("login.elsewhere.heading") })).toBeVisible();
    await expect(phonePage).toHaveURL(/\/logga-in\/lank\?token=/);
    await phone.close();

    await page.getByLabel(text("login.code.label")).fill(mail.code!);
    await page.getByRole("button", { name: text("login.code.submit") }).click();
    await expect(page).toHaveURL(PATHS.member.home);
    await expect(page.getByText("Sara Bergström", { exact: true })).toBeVisible();
  });

  test("a wrong code says so on the page it was typed on", async ({ page }) => {
    await clearMail(MEMBERS.anders);
    await requestLink(page, "member", MEMBERS.anders);
    const code = (await waitForMail(MEMBERS.anders)).code!;
    await page.getByLabel(text("login.code.label")).fill(code === "000000" ? "111111" : "000000");
    await page.getByRole("button", { name: text("login.code.submit") }).click();
    await expect(page).toHaveURL(`${PATHS.member.sent}?fel`);
    await expect(page.getByRole("alert").filter({ hasText: text("login.code.failed") })).toBeVisible();

    await page.getByLabel(text("login.code.label")).fill(code);
    await page.getByRole("button", { name: text("login.code.submit") }).click();
    await expect(page).toHaveURL(PATHS.member.home);
  });

  test("the login holds while the link page's fonts are still loading", async ({ page, playwright, browserName }) => {
    // Spring Session JDBC saves every request's session by its row, id
    // included. When a login only renamed the session, a static file request
    // that loaded it under the old id and saved after the login put the old id
    // back, and the new cookie found no session. A real browser sends exactly
    // those requests, since login-link.js submits before the page's fonts and
    // stylesheet are done. The race did not lose every time: 17 of 20 attempts
    // here on 2026-09-23, 2 of 10 with curl, so the test tries 20 times.
    //
    // The requests come from a request context of their own, not the browser,
    // so the timing is the same in every project; running it once is enough.
    test.skip(browserName !== "chromium", "sends no browser requests");
    const lost: number[] = [];
    for (let attempt = 0; attempt < 20; attempt++) {
      // The client asks for the link itself, since a link works only in the
      // browser that asked for it.
      await clearLinkRequests();
      await clearMail(MEMBERS.erik);
      const client = await playwright.request.newContext({ baseURL: SITE });
      const loginPage = await (await client.get(PATHS.member.login)).text();
      const loginCsrf = /name="_csrf" value="([^"]*)"/.exec(loginPage)![1];
      await client.post(PATHS.member.login, { form: { email: MEMBERS.erik, _csrf: loginCsrf } });
      const link = (await waitForMail(MEMBERS.erik)).link;
      const linkPage = await (await client.get(link)).text();
      const csrf = /name="_csrf" value="([^"]*)"/.exec(linkPage)![1];
      const token = new URL(link).searchParams.get("token")!;
      await Promise.all([
        ...Array.from({ length: 8 }, () => client.get("/fonts/montserrat-latin-normal.woff2")),
        client.post(PATHS.member.link, { form: { token, _csrf: csrf }, maxRedirects: 0 }),
      ]);
      const home = await client.get(PATHS.member.home, { maxRedirects: 0 });
      if (home.status() !== 200) {
        lost.push(attempt);
      }
      await client.dispose();
    }
    expect(lost, "attempts that ended logged out").toEqual([]);
  });
});

test.describe("what a person might get wrong", () => {
  test("the login page shows no passkey error before anyone asks for a passkey", async ({ page }) => {
    // Headless Chromium offers autofill but refuses the request it starts.
    for (const path of [PATHS.member.login, `${PATHS.member.login}?fel`, PATHS.administrator.login]) {
      await page.goto(path);
      await page.waitForLoadState("networkidle");
      await expect(page.getByText(text("passkey.error.login")), path).toBeHidden();
    }
  });

  test("an unknown address gets the same page as a member, and no mail", async ({ page }) => {
    await requestLink(page, "member", MEMBERS.erik);
    const known = await page.locator("main").innerText();

    const stranger = "nobody-here@example.test";
    await clearMail(stranger);
    await requestLink(page, "member", stranger);
    expect(await page.locator("main").innerText()).toBe(known);
    expect(await mailCountAfterWait(stranger)).toBe(0);
  });

  test("a member's address on the administrator page gets no mail", async ({ page }) => {
    await clearMail(MEMBERS.erik);
    await requestLink(page, "administrator", MEMBERS.erik);
    await expect(page.getByRole("heading", { name: text("login.sent.heading") })).toBeVisible();
    expect(await mailCountAfterWait(MEMBERS.erik)).toBe(0);
  });

  test("an address typed with capitals and spaces still gets a link", async ({ page }) => {
    await clearMail(MEMBERS.anders);
    await page.goto(PATHS.member.login);
    await page.locator("#email").fill("  Anders.Sjoberg@EXAMPLE.test ");
    await page.locator(`form[action="${PATHS.member.login}"] button[type=submit]`).click();
    await expect(page).toHaveURL(PATHS.member.sent);
    await page.goto((await waitForMail(MEMBERS.anders)).link);
    await expect(page).toHaveURL(PATHS.member.home);
  });

  test("a blank address with the browser's check bypassed shows the form again", async ({ page }) => {
    await page.goto(PATHS.member.login);
    await page.locator("#email").evaluate((input: HTMLInputElement) => {
      input.required = false;
      input.value = "   ";
    });
    const response = page.waitForResponse((r) => r.request().method() === "POST");
    await page.locator(`form[action="${PATHS.member.login}"] button[type=submit]`).click();
    expect((await response).status()).toBeLessThan(500);
    await expect(page.locator("#email")).toBeVisible();
  });

  test("a link works only once", async ({ page }) => {
    const link = await mailedLink(page, "member", MEMBERS.erik);
    await page.goto(link);
    await expect(page).toHaveURL(PATHS.member.home);
    await logOut(page, "member");

    await page.goto(link);
    await expect(page).toHaveURL(`${PATHS.member.login}?fel`);
    await expect(linkFailed(page)).toBeVisible();
  });

  test("an expired link does not log in", async ({ page }) => {
    const link = await mailedLink(page, "member", MEMBERS.erik);
    await expireLoginLinks(MEMBERS.erik);
    await page.goto(link);
    await expect(page).toHaveURL(`${PATHS.member.login}?fel`);
    await expect(linkFailed(page)).toBeVisible();
  });

  for (const [name, mangle] of [
    ["a character added", (token: string) => `${token}x`],
    ["the end cut off, as a mail program wrapping the line might", (token: string) => token.slice(0, 10)],
    ["an empty token", () => ""],
    ["markup in the token", () => `"><script>window.injected=1</script>`],
  ] as const) {
    test(`a link with ${name} lands on the login page with the error`, async ({ page }) => {
      const link = new URL(await mailedLink(page, "member", MEMBERS.erik));
      link.searchParams.set("token", mangle(link.searchParams.get("token")!));
      const responses: number[] = [];
      page.on("response", (response) => responses.push(response.status()));
      await page.goto(link.pathname + link.search);
      await expect(page).toHaveURL(`${PATHS.member.login}?fel`);
      await expect(linkFailed(page)).toBeVisible();
      expect(responses.filter((status) => status >= 500)).toEqual([]);
      expect(await page.evaluate(() => (window as { injected?: number }).injected)).toBeUndefined();
    });
  }

  test("the link page without any token goes back to the login page", async ({ page }) => {
    await page.goto(PATHS.member.link);
    await expect(page).toHaveURL(`${PATHS.member.login}?fel`);
  });

  test("a member's link pasted under /admin does not log in, and still works where it belongs", async ({ page }) => {
    const link = new URL(await mailedLink(page, "member", MEMBERS.karin));
    await page.goto(PATHS.administrator.link + link.search);
    await expect(page).toHaveURL(`${PATHS.administrator.login}?fel`);

    await page.goto(link.pathname + link.search);
    await expect(page).toHaveURL(PATHS.member.home);
  });

  test("an older link still works after asking for a newer one", async ({ page }) => {
    const first = await mailedLink(page, "member", MEMBERS.erik);
    const second = await mailedLink(page, "member", MEMBERS.erik);
    expect(second).not.toBe(first);

    await page.goto(first);
    await expect(page).toHaveURL(PATHS.member.home);
    await logOut(page, "member");
    await page.goto(second);
    await expect(page).toHaveURL(PATHS.member.home);
  });

  test("pressing the send button twice still leaves a link that works", async ({ page }) => {
    await clearMail(MEMBERS.maria);
    await page.goto(PATHS.member.login);
    await page.locator("#email").fill(MEMBERS.maria);
    await page.locator(`form[action="${PATHS.member.login}"] button[type=submit]`).dblclick();
    await expect(page).toHaveURL(PATHS.member.sent);
    await page.goto((await waitForMail(MEMBERS.maria)).link);
    await expect(page).toHaveURL(PATHS.member.home);
  });

  test("the back button after logging out does not show the member's details", async ({ page }) => {
    await linkLogin(page, "member", MEMBERS.erik);
    await expect(page.getByText("Erik Lindqvist", { exact: true })).toBeVisible();
    await logOut(page, "member");
    await page.goBack();
    await expect(page.getByText("Erik Lindqvist", { exact: true })).toHaveCount(0);
  });

  test("a member who opens the administrator page lands on a login that says it is not theirs", async ({ page }) => {
    await linkLogin(page, "member", MEMBERS.erik);
    await page.goto(PATHS.administrator.home);
    await expect(page).toHaveURL(PATHS.administrator.login);
    await expect(page.getByText(text("login.administrator.audience"))).toBeVisible();
    await page.getByRole("link", { name: text("login.administrator.member") }).click();
    await expect(page).toHaveURL(PATHS.member.login);
  });

  test("a member opening someone else's link, on a shared computer, becomes that person", async ({ page }) => {
    await linkLogin(page, "member", MEMBERS.erik);
    await page.goto(await mailedLink(page, "member", MEMBERS.maria));
    await expect(page).toHaveURL(PATHS.member.home);
    await expect(page.getByText("Maria Lindqvist", { exact: true })).toBeVisible();
    await expect(page.getByText("Erik Lindqvist", { exact: true })).toHaveCount(0);
  });

  test("logging in as the other kind replaces the first login", async ({ page }) => {
    // 0015, "Two filter chains": a browser holds one login at a time.
    await linkLogin(page, "administrator", ADMINISTRATORS.karin);
    await linkLogin(page, "member", MEMBERS.karin);
    await page.goto(PATHS.administrator.home);
    await expect(page).toHaveURL(PATHS.administrator.login);
  });

  test("the sixth link request within the hour sends no mail, and the page does not say so", async ({ page }) => {
    await clearMail(MEMBERS.sara);
    for (let i = 0; i < 5; i++) {
      await requestLink(page, "member", MEMBERS.sara);
    }
    await expect.poll(async () => mailCountAfterWait(MEMBERS.sara, 0), { timeout: 10_000 }).toBe(5);
    const sent = await page.locator("main").innerText();

    await requestLink(page, "member", MEMBERS.sara);
    expect(await page.locator("main").innerText()).toBe(sent);
    expect(await mailCountAfterWait(MEMBERS.sara)).toBe(5);
  });

  test("a login page left open until its session is gone asks again, then sends a link", async ({ page, context }) => {
    // The session cookie disappears when the browser restarts before any login,
    // or when the session times out, while the page with its CSRF token stays
    // open in a tab. The CSRF check refuses the form, and the person lands on
    // the same page with a line asking them to send it again. The address is
    // not carried over.
    await clearMail(MEMBERS.johan);
    await page.goto(PATHS.member.login);
    await page.locator("#email").fill(MEMBERS.johan);
    await context.clearCookies();
    const submit = page.locator(`form[action="${PATHS.member.login}"] button[type=submit]`);
    await submit.click();
    await expect(page).toHaveURL(`${PATHS.member.login}?gammal`);
    await expect(page.getByRole("alert")).toHaveText(text("form.stale"));
    expect(await mailCountAfterWait(MEMBERS.johan)).toBe(0);

    await page.locator("#email").fill(MEMBERS.johan);
    await submit.click();
    await expect(page).toHaveURL(PATHS.member.sent);
    expect(await mailCountAfterWait(MEMBERS.johan)).toBe(1);
  });
});
