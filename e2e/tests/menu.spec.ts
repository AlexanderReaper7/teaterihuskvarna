import { expect, test } from "@playwright/test";
import { text } from "../support/copy";

// The header menu (#92): a button on a narrow screen, the open list on a wide
// one, and every link reachable without JavaScript.

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

        test("every link is shown and the button is not", async ({ page }) => {
            await page.goto("/");
            await expect(page.locator(".site-header button")).toBeHidden();
            await expect(page.locator("#huvudmeny").getByRole("link", { name: text("nav.calendar") })).toBeVisible();
            await expect(page.locator("#huvudmeny").getByRole("link", { name: text("nav.member") })).toBeVisible();
        });
    });
});

test("on a wide screen the menu is open and the button is hidden", async ({ page }) => {
    await page.goto("/");
    await expect(page.locator(".site-header button")).toBeHidden();
    await expect(page.locator("#huvudmeny").getByRole("link", { name: text("nav.calendar") })).toBeVisible();
});
