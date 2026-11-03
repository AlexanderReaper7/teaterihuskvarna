import { expect, test } from "@playwright/test";
import { details, scan } from "../support/pages";

test("page scans reject a missing page instead of passing its error layout", async ({ page }) => {
  await expect(scan(page, ["/missing-page-scan-target"], async () => []))
    .rejects.toMatchObject({ matcherResult: { actual: 404, expected: 200 } });
});

test("page scans reject a redirect to login instead of checking the login form", async ({ page }) => {
  await expect(scan(page, ["/admin/medlemmar"], async () => []))
    .rejects.toMatchObject({ matcherResult: { actual: "/admin/logga-in", expected: "/admin/medlemmar" } });
});

test("detail scans reject an empty list instead of silently checking no pages", async ({ page }) => {
  await expect(details(page, "/nyheter", "/missing-details/"))
    .rejects.toThrow(/detail pages on \/nyheter/);
});
