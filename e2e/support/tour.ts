// What the walkthroughs' tests share: the card /js/tour.js shows, and what
// holds for it on every step, in tests/tour.spec.ts for the administrators
// and tests/member-tour.spec.ts for the members.
import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { expect, type Locator, type Page } from "@playwright/test";
import { text } from "./copy";

/// A walkthrough's steps in order, and the first part of their keys in the copy.
export type Tour = { steps: string[]; keys: string };

/// The steps in the order a template under `src/main/jte/tag` lists them, by the name in their keys.
export function tourSteps(template: string, keys: string): string[] {
  return [...readFileSync(resolve(__dirname, "../../src/main/jte/tag", template), "utf8")
    .matchAll(new RegExp(`tourStep\\(title = copy\\.text\\("${keys}\\.(\\w+)\\.title"\\)`, "g"))]
    .map((match) => match[1]);
}

export const card = (page: Page): Locator => page.locator("section.tour");
export const next = (page: Page): Promise<void> =>
  card(page).getByRole("button", { name: text("tour.next"), exact: true }).click();

/// Where the card lies: over the page and not in it, and never on what the
/// step is about. In the corner it touches no part of the target. As a bar it
/// cannot help lying over a target taller than the window, so there the
/// target's top is above it and the page has the bar's height after its end,
/// which lets the rest scroll out from under it. Its Nästa is in the window
/// either way, also when the card scrolls its text.
export async function expectOverlay(page: Page, step: string): Promise<"corner" | "bar"> {
  await expect(card(page).locator("[data-tour-next]"), `Nästa on ${step}`).toBeInViewport({ ratio: 1 });
  let place: "corner" | "bar" = "corner";
  // The bar's height reaches the page a frame after the bar changes.
  await expect(async () => {
    const at = await card(page).evaluate((element) => {
      const target = document.querySelector(".tour-target");
      const outlined = target?.parentElement?.matches(".table-scroll") ? target.parentElement : target;
      const over = element.getBoundingClientRect();
      const under = outlined?.getBoundingClientRect() ?? null;
      return {
        position: getComputedStyle(element).position,
        bar: element.classList.contains("tour-bar"),
        top: over.top,
        height: over.height,
        window: window.innerHeight,
        padding: parseFloat(getComputedStyle(document.body).paddingBottom),
        targetTop: under ? under.top : null,
        onTarget: under !== null && over.left < under.right && under.left < over.right
          && over.top < under.bottom && under.top < over.bottom,
      };
    });
    place = at.bar ? "bar" : "corner";
    expect(at.position, `the card for ${step} is fixed to the window`).toBe("fixed");
    if (at.bar) {
      expect(Math.abs(at.padding - at.height), `room after the page for the bar on ${step}`).toBeLessThan(1);
    } else {
      expect(at.padding, `room after the page without a bar on ${step}`).toBe(0);
      expect(at.onTarget, `the corner card lies on the target of ${step}`).toBe(false);
    }
    if (at.targetTop !== null) {
      expect(at.targetTop, `the top of the target of ${step}`).toBeGreaterThanOrEqual(0);
      expect(at.targetTop, `the top of the target of ${step}`).toBeLessThan(at.bar ? at.top : at.window);
    }
  }).toPass({ timeout: 5000 });
  return place;
}

/// The card stands on the step: its number, title and text, and focus, so a screen reader reads it. With a target, the
/// target is outlined, the card stands before the block that holds it in the
/// page's order, and it lies over the page without lying on the target.
export async function expectTourStep(page: Page, tour: Tour, step: string, target?: string): Promise<void> {
  const { steps, keys } = tour;
  const number = String(steps.indexOf(step) + 1);
  await expect(card(page).locator("[data-tour-progress]"))
    .toHaveText(text("tour.progress", number, String(steps.length)));
  await expect(card(page).getByRole("heading", { level: 2 })).toHaveText(text(`${keys}.${step}.title`));
  await expect(card(page).locator("[data-tour-body]")).toHaveText(text(`${keys}.${step}.body`));
  await expect(card(page)).toBeFocused();
  await expect(card(page)).toBeInViewport();
  if (target) {
    await expect(page.locator(".tour-target")).toHaveCount(1);
    expect(await page.locator(target).first().evaluate((element) => element.classList.contains("tour-target")),
      `${step} outlines ${target}`).toBe(true);
    expect(await card(page).evaluate((element) =>
      element.nextElementSibling?.contains(document.querySelector(".tour-target")) ?? false),
    `the card for ${step} stands before its target in the page's order`).toBe(true);
  } else {
    await expect(page.locator(".tour-target")).toHaveCount(0);
  }
  await expectOverlay(page, step);
}
