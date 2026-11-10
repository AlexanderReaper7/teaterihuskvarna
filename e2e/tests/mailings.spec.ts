// The mailing preview (R023). The mail is a response of its own, shown in a
// frame that runs no script and cannot use the administrator's session. Why it
// is built this way: docs/decisions/0026, "The mailing preview".
import { expect, test, type Page, type Response } from "@playwright/test";
import { linkLogin, mailedLink } from "../support/auth";
import { text } from "../support/copy";
import { clearLinkRequests, resetAdministrators, sql } from "../support/db";
import { ADMINISTRATORS, PATHS } from "../support/site";

/// The address the preview page's frame fetches the mail from.
const MAIL = /\/admin\/utskick\/forhandsgranska\/[0-9a-f-]{36}$/;
const SUBJECT = "Höstens föreställningar";
const INTRO = "Hej alla! Nu börjar hösten.";

/// A document that shows what it was allowed to do. Its script rewrites the
/// paragraph, asks the site for a marked address and then moves the page
/// around the frame. With script refused, the paragraph keeps its text, the
/// marked address is never asked for and the page stays where it is.
const MARK = "/css/site.css?asked-by-the-mail";
const HOSTILE = `<!DOCTYPE html><html><head><title>mail</title></head><body><p>static text</p>
<script>
document.querySelector("p").textContent = "script ran";
fetch("${MARK}").finally(() => { top.location = "/admin"; });
</script>
</body></html>`;

test.beforeEach(async () => {
  await resetAdministrators();
  await clearLinkRequests();
});

/// Fills the form and presses preview.
///
/// @return the response the frame got for the mail
async function preview(page: Page): Promise<Response> {
  await page.goto("/admin/utskick");
  await page.locator("#audience").selectOption({ index: 1 });
  await page.locator("#subject").fill(SUBJECT);
  await page.locator("#intro").fill(INTRO);
  await page.locator('input[name="events"][value="kulturnatten"]').check();
  const mail = page.waitForResponse((response) => MAIL.test(response.url()));
  await page.getByRole("button", { name: text("adminMailings.form.preview") }).click();
  return mail;
}

async function expectTheChosenMail(page: Page, response: Response): Promise<void> {
  expect(response.status()).toBe(200);
  const frame = page.locator("iframe.mail-preview");
  // No allowance at all: no allow-scripts and no allow-same-origin.
  await expect(frame).toHaveAttribute("sandbox", "");
  const mail = page.frameLocator("iframe.mail-preview");
  await expect(mail.getByRole("heading", { level: 1, name: SUBJECT })).toBeVisible();
  await expect(mail.getByText(INTRO)).toBeVisible();
  await expect(mail.getByRole("heading", { name: "Kulturnatten på torget" })).toBeVisible();
}

test("the preview shows the chosen mail in a frame and makes no mailing", async ({ page }) => {
  await linkLogin(page, "administrator", ADMINISTRATORS.ada);
  const [before] = await sql<{ count: string }>("SELECT count(*) FROM mailing");

  await expectTheChosenMail(page, await preview(page));

  // The frame's size and border come from the stylesheet, not from the tag.
  const frame = page.locator("iframe.mail-preview");
  await expect(frame).not.toHaveAttribute("style");
  const box = (await frame.boundingBox())!;
  expect(box.height).toBeGreaterThan(600);
  expect(box.width).toBeGreaterThan(600);
  const [after] = await sql<{ count: string }>("SELECT count(*) FROM mailing");
  expect(after.count).toBe(before.count);
});

test("a preview whose form is refused as stale goes back to the mailing page, which says so", async ({ page }) => {
  // The token is too old with the session still there after a login in
  // another tab. The refused form is sent back to the page it was on, and the
  // preview is drawn by a POST: a GET of its address is read as a mailing
  // whose id is not a number, and gave the error page. The administrator now
  // lands one step up, on the mailing page.
  await linkLogin(page, "administrator", ADMINISTRATORS.ada);
  const [before] = await sql<{ count: string }>("SELECT count(*) FROM mailing");
  await preview(page);
  await page.locator('main input[name="_csrf"]').evaluateAll((fields) => {
    for (const field of fields) {
      (field as HTMLInputElement).value = "too-old";
    }
  });

  await page.getByRole("button", { name: text("adminMailings.preview.prepare") }).click();

  await expect(page).toHaveURL("/admin/utskick?gammal");
  await expect(page.getByRole("alert").filter({ hasText: text("form.stale") })).toBeVisible();
  await expect(page.locator("main h1")).toHaveText(text("adminMailings.heading"));
  const [after] = await sql<{ count: string }>("SELECT count(*) FROM mailing");
  expect(after.count).toBe(before.count);
});

test("the preview shows the mail without JavaScript", async ({ browser }) => {
  const context = await browser.newContext({ javaScriptEnabled: false });
  const page = await context.newPage();
  await page.goto(await mailedLink(page, "administrator", ADMINISTRATORS.ada));
  await page.getByRole("button", { name: text("login.link.submit") }).click();
  await expect(page).toHaveURL(PATHS.administrator.home);

  await expectTheChosenMail(page, await preview(page));
  await context.close();
});

test("a script in the previewed mail does not run, in the frame or in a tab of its own", async ({ page }) => {
  await linkLogin(page, "administrator", ADMINISTRATORS.ada);
  const asked: string[] = [];
  page.on("request", (request) => {
    if (request.url().endsWith(MARK)) {
      asked.push(request.frame().url());
    }
  });

  // The control: served with nothing to stop it, the document does all it tries.
  await page.route("**/admin/unguarded-mail", (route) => route.fulfill({ contentType: "text/html", body: HOSTILE }));
  await page.goto("/admin/unguarded-mail");
  await expect(page).toHaveURL(PATHS.administrator.home);
  expect(asked).toHaveLength(1);
  asked.length = 0;

  // The same document in place of the mail, under the headers the application
  // really sends with the mail.
  await page.route(MAIL, async (route) => {
    const real = await route.fetch();
    const headers = real.headers();
    delete headers["content-length"];
    await route.fulfill({ status: real.status(), headers, body: HOSTILE });
  });
  const response = await preview(page);
  expect(response.headers()["content-security-policy"]).toMatch(/^sandbox;/);
  await page.waitForLoadState("load");
  await expect(page.frameLocator("iframe.mail-preview").locator("p")).toHaveText("static text");
  await expect(page).toHaveURL("/admin/utskick/forhandsgranska");
  expect(asked).toEqual([]);

  // Opened in a tab the frame's sandbox attribute is gone, and the response's
  // own policy has to refuse the script.
  await page.goto(response.url());
  await expect(page.locator("p")).toHaveText("static text");
  await expect(page).toHaveURL(MAIL);
  expect(asked).toEqual([]);
});
