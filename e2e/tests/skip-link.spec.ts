import { expect, test } from "@playwright/test";
import { text } from "../support/copy";

test("the skip link jumps past the menu into the main content", async ({ page }) => {
    await page.goto("/");

    await page.keyboard.press("Tab");
    const skipLink = page.getByRole("link", { name: text("site.skip") });
    await expect(skipLink).toBeFocused();
    await expect(skipLink).toBeVisible();

    await page.keyboard.press("Enter");
    await expect(page).toHaveURL(/#innehall$/);

    await page.keyboard.press("Tab");
    await expect(page.locator("main *:focus")).toBeVisible();
});
