import { expect, test, type Page } from "@playwright/test";
import { text } from "../support/copy";

// The header menu (#92): a button on a narrow screen, the open list on a wide
// one, and every link reachable without JavaScript. On a narrow screen the
// menu is collapsed from the first paint, so the page does not jump when its
// scripts have run.

const menuButton = (page: Page) => page.getByRole("button", { name: text("nav.menu") });
const calendarLink = (page: Page) => page.locator("#huvudmeny").getByRole("link", { name: text("nav.calendar") });

/// The sum of the page's layout shifts so far, as Chromium's Layout
/// Instability API reports them, without those that follow a press or a key.
function layoutShift(page: Page): Promise<number> {
    return page.evaluate(() => new Promise<number>((resolve) => {
        let total = 0;
        new PerformanceObserver((list) => {
            for (const entry of list.getEntries() as (PerformanceEntry & { value: number; hadRecentInput: boolean })[]) {
                if (!entry.hadRecentInput) {
                    total += entry.value;
                }
            }
        }).observe({ type: "layout-shift", buffered: true });
        // The buffered entries arrive in a task of their own, after observe().
        setTimeout(() => resolve(total), 500);
    }));
}

test.describe("on a narrow screen", () => {
    test.use({ viewport: { width: 375, height: 800 } });

    test("the menu button opens the menu, and Escape closes it and returns focus", async ({ page }) => {
        await page.goto("/");
        const button = page.getByRole("button", { name: text("nav.menu") });
        const calendar = page.locator("#huvudmeny").getByRole("link", { name: text("nav.calendar") });
        await expect(button).toBeVisible();
        await expect(button).toHaveAttribute("aria-expanded", "false");
        await expect(calendar).toBeHidden();

        await button.click();
        await expect(button).toHaveAttribute("aria-expanded", "true");
        await expect(calendar).toBeVisible();

        await calendar.focus();
        await page.keyboard.press("Escape");
        await expect(calendar).toBeHidden();
        await expect(button).toBeFocused();

        await page.keyboard.press("Enter");
        await expect(calendar).toBeVisible();
    });

    test.describe("without JavaScript", () => {
        test.use({ javaScriptEnabled: false });

        test("every link is shown and works, and the button is not shown", async ({ page }) => {
            await page.goto("/");
            await expect(page.locator(".site-header button")).toBeHidden();
            await expect(page.locator("#huvudmeny").getByRole("link", { name: text("nav.member") })).toBeVisible();
            await expect(calendarLink(page)).toBeVisible();
            await calendarLink(page).click();
            await expect(page).toHaveURL("/kalender");
            await expect(calendarLink(page)).toBeVisible();
        });
    });

    test("the menu stays open when its script does not arrive", async ({ page }) => {
        await page.route("**/js/menu.js", (route) => route.abort());
        await page.goto("/");
        await expect(page.locator("main h1")).toBeVisible();
        await expect(page.locator(".site-header button")).toBeHidden();
        await expect(calendarLink(page)).toBeVisible();
    });
});

test.describe("on a phone", () => {
    test.use({ viewport: { width: 360, height: 740 } });

    // forms.js is deferred, and DOMContentLoaded waits for every deferred
    // script, so holding it back holds the page where a slow network would:
    // drawn, with the scripts that run at DOMContentLoaded still to come.
    test("the menu is collapsed while the page is still loading, and its button already works", async ({ page }) => {
        let release = () => {};
        const held = new Promise<void>((resolve) => { release = resolve; });
        await page.route("**/js/forms.js", async (route) => {
            await held;
            await route.continue();
        });
        try {
            await page.goto("/nyheter", { waitUntil: "commit" });
            await expect(page.locator("main h1")).toBeVisible();
            await expect(calendarLink(page)).toBeHidden();
            await expect(menuButton(page)).toBeVisible();
            await expect(menuButton(page)).toHaveAttribute("aria-expanded", "false");

            await menuButton(page).click();
            await expect(calendarLink(page)).toBeVisible();
        } finally {
            release();
        }
        // What the visitor opened stays open when the rest of the page arrives.
        await page.waitForLoadState("load");
        await expect(menuButton(page)).toHaveAttribute("aria-expanded", "true");
        await expect(calendarLink(page)).toBeVisible();
        await menuButton(page).click();
        await expect(calendarLink(page)).toBeHidden();
    });

    for (const path of ["/nyheter", "/bli-medlem"]) {
        test(`${path} does not jump when the menu's script has run`, async ({ page, browserName }) => {
            test.skip(browserName !== "chromium", "Only Chromium has the Layout Instability API.");
            // As late as on a phone's network. From localhost the script
            // arrives before the first paint in about half the loads, and a
            // page that never painted the open menu has nothing to shift.
            await page.route("**/js/menu.js", async (route) => {
                await new Promise((resolve) => setTimeout(resolve, 300));
                await route.continue();
            });
            await page.goto(path);
            await expect(menuButton(page)).toBeVisible();
            // Google's limit for a good page. The open menu collapsing after
            // the first paint measured 0.708.
            expect(await layoutShift(page)).toBeLessThan(0.1);
        });
    }
});

test("on a wide screen the menu is open and the button is hidden", async ({ page }) => {
    await page.goto("/");
    await expect(page.locator(".site-header button")).toBeHidden();
    await expect(page.locator("#huvudmeny").getByRole("link", { name: text("nav.calendar") })).toBeVisible();
});
