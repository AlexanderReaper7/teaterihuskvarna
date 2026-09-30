// R015: member documents uploaded on /admin/handlingar, against a real Tomcat.
// The integration tests use MockMvc, which does not parse a multipart body the
// way Tomcat does, so only a browser shows what the size limits do.
import { expect, test, type Page } from "@playwright/test";
import { linkLogin } from "../support/auth";
import { text } from "../support/copy";
import { clearLinkRequests, resetAdministrators, sql } from "../support/db";
import { ADMINISTRATORS } from "../support/site";

const MB = 1024 * 1024;
const TITLE = "Storlekstest";

test.beforeEach(async () => {
  await resetAdministrators();
  await clearLinkRequests();
  await sql("DELETE FROM member_document WHERE title = $1", [TITLE]);
});
test.afterAll(async () => {
  await sql("DELETE FROM member_document WHERE title = $1", [TITLE]);
});

/// A PDF header followed by padding, `bytes` long in all.
function pdf(bytes: number): Buffer {
  const file = Buffer.alloc(bytes, " ");
  file.write("%PDF-1.7\n");
  return file;
}

async function upload(page: Page, bytes: number): Promise<void> {
  await linkLogin(page, "administrator", ADMINISTRATORS.ada);
  await page.goto("/admin/handlingar");
  await page.getByLabel(text("adminDocuments.upload.title"), { exact: true }).fill(TITLE);
  await page.getByLabel(text("adminDocuments.upload.kind"), { exact: true })
    .selectOption({ label: text("document.kind.MEMBER_LETTER") });
  await page.getByLabel(text("adminDocuments.upload.publishedOn"), { exact: true }).fill("2026-09-01");
  await page.getByLabel(text("adminDocuments.upload.file"), { exact: true })
    .setInputFiles({ name: "handling.pdf", mimeType: "application/pdf", buffer: pdf(bytes) });
  await page.getByRole("button", { name: text("adminDocuments.upload.submit"), exact: true }).click();
}

test("a PDF of exactly 10 MB is accepted", async ({ page }) => {
  await upload(page, 10 * MB);

  await expect(page.getByText(text("adminDocuments.uploaded", TITLE))).toBeVisible();
});

test("a file just over 10 MB gets the page's own message", async ({ page }) => {
  await upload(page, 10 * MB + MB / 2);

  await expect(page.getByRole("alert")).toContainText(text("adminDocuments.error.tooLarge"));
  expect(await sql("SELECT 1 FROM member_document WHERE title = $1", [TITLE])).toHaveLength(0);
});

test("a file over the request limit comes back to the page saying so, not as a stale form", async ({ page }) => {
  await upload(page, 11 * MB);

  await expect(page).toHaveURL(/\/admin\/handlingar\?forstor$/);
  await expect(page.getByRole("alert")).toContainText(text("adminDocuments.error.tooLarge"));
});
