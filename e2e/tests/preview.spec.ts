// The script that connects a preview page to the Studio's Presentation tool is
// built in the Dockerfile's visual-editing stage, which no Maven test runs.
// docs/decisions/0021-content-from-sanity.md.
import { expect, test } from "@playwright/test";

test("the image serves the visual-editing script and its styles", async ({ request }) => {
  const script = await request.get("/js/visual-editing.js");
  expect(script.status()).toBe(200);
  expect(script.headers()["content-type"]).toContain("javascript");
  // The channel Presentation and the page talk over, so the file is Sanity's
  // bundle and not an empty placeholder.
  expect(await script.text()).toContain("sanity/channels");

  const styles = await request.get("/css/visual-editing.css");
  expect(styles.status()).toBe(200);
  expect(styles.headers()["content-type"]).toContain("text/css");

  // The bundled packages' licences, which the minified file mostly drops.
  // @vercel/stega is the one under MPL-2.0.
  const licenses = await request.get("/js/visual-editing.licenses.txt");
  expect(licenses.status()).toBe(200);
  expect(await licenses.text()).toContain("@vercel/stega 1.1.0, MPL-2.0");
});

// The fixture accepts no preview secret, so this stack never shows the
// banner. The test puts the banner's markup from src/main/jte/layout.jte on
// a public page instead. Its role once let the message box rule paint the
// pale panel colour behind its white text.
test("the preview banner is white on black, not a message box", async ({ page }) => {
  await page.goto("/");
  await page.evaluate(() => document.body.insertAdjacentHTML("afterbegin",
    '<div class="preview-banner" role="status"><p>Förhandsgranskning <a href="/forhandsgranska/avsluta">Avsluta</a></p></div>'));
  const banner = page.locator(".preview-banner");
  await expect(banner).toHaveCSS("background-color", "rgb(0, 0, 0)");
  await expect(banner).toHaveCSS("color", "rgb(255, 255, 255)");
  await expect(banner.locator("a")).toHaveCSS("color", "rgb(255, 255, 255)");
  await expect(banner).toHaveCSS("border-left-width", "0px");
  await expect(banner).toHaveCSS("margin-bottom", "0px");
});
