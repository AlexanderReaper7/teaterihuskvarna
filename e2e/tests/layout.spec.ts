// Every page, at a phone's width up to the user's own 2560 px view, but three.
// /dev exists only under the dev profile. The other two only a mailed login
// link opens: the link's page, which submits itself, and the page saying to
// open it in the browser that asked. Checks what a person would see as broken:
// sideways scrolling, buttons touching each other, buttons poking out of the
// box they sit in, words or phone numbers in a table broken in two, a menu on
// two lines with room for one, a field and its button of different heights,
// and radio buttons or checkboxes away from their text.
import { expect, test, type Page } from "@playwright/test";
import { linkLogin } from "../support/auth";
import { text } from "../support/copy";
import { clearLinkRequests, resetAdministrators, sql } from "../support/db";
import { clearMail, waitForMail } from "../support/mail";
import { ADMINISTRATORS, freshAddress } from "../support/site";
import { ADMIN, MEMBER, PUBLIC, details, householdPages, prepareAdministratorPages, saveMailingDraft, scan } from "../support/pages";

test.beforeEach(async () => {
  await resetAdministrators();
  await clearLinkRequests();
});

const WIDTHS = [320, 420, 900, 2560];

/// The register lists every member, including those other specs add, so its
/// layout is checked with the widest of them whatever order the specs run in:
/// admin.spec's name with a long word, and an address of the kind freshAddress
/// writes, which Firefox does not break after a hyphen before a digit.
const WIDE_MEMBER = { name: `<img src=x onerror="window.injected=1">`, email: "markup-1793560718668-820173@example.test" };

async function wideMember(): Promise<void> {
  const [found] = await sql("SELECT 1 FROM account WHERE LOWER(email) = $1", [WIDE_MEMBER.email]);
  if (!found) {
    const [person] = await sql<{ id: string }>(
      "INSERT INTO member (full_name, created_at) VALUES ($1, now()) RETURNING id", [WIDE_MEMBER.name]);
    await sql("INSERT INTO account (member_id, email, created_at) VALUES ($1, $2, now())", [person.id, WIDE_MEMBER.email]);
  }
}

/// Every problem found on the page as it is now, as readable sentences.
async function problems(page: Page): Promise<string[]> {
  return page.evaluate(() => {
    const found: string[] = [];
    const root = document.documentElement;
    if (root.scrollWidth > root.clientWidth) {
      found.push(`the page scrolls sideways: ${root.scrollWidth} px wide in ${root.clientWidth} px`);
    }
    const visible = [...document.querySelectorAll("button")].filter((b) => b.getClientRects().length > 0);
    const name = (b: Element) => JSON.stringify((b.textContent ?? "").trim().replace(/\s+/g, " "));

    // Buttons side by side or stacked need space between them.
    for (const a of visible) {
      for (const b of visible) {
        if (a === b || a.parentElement !== b.parentElement || !(a.compareDocumentPosition(b) & 4)) {
          continue;
        }
        const ra = a.getBoundingClientRect();
        const rb = b.getBoundingClientRect();
        const sameRow = ra.top < rb.bottom && rb.top < ra.bottom;
        const gap = sameRow
          ? Math.max(ra.left, rb.left) - Math.min(ra.right, rb.right)
          : Math.max(ra.top, rb.top) - Math.min(ra.bottom, rb.bottom);
        if (gap < 4) {
          found.push(`${name(a)} and ${name(b)} are ${gap.toFixed(1)} px apart`);
        }
      }
    }

    // A button stays inside the padding of every box around it.
    for (const button of visible) {
      const r = button.getBoundingClientRect();
      for (let box = button.parentElement; box && box !== document.body; box = box.parentElement) {
        const style = getComputedStyle(box);
        const inner = box.getBoundingClientRect();
        const left = inner.left + parseFloat(style.borderLeftWidth) + parseFloat(style.paddingLeft);
        const right = inner.right - parseFloat(style.borderRightWidth) - parseFloat(style.paddingRight);
        if (r.left < left - 0.5 || r.right > right + 0.5) {
          const where = box.getAttribute("role") ?? box.className ?? box.tagName;
          found.push(`${name(button)} reaches outside the padding of <${box.tagName.toLowerCase()}> ${where}`
            + ` (${r.left.toFixed(0)}-${r.right.toFixed(0)} in ${left.toFixed(0)}-${right.toFixed(0)})`);
          break;
        }
      }
    }
    // A word in a table breaks only where a line may break anyway, after a
    // hyphen, unless it is wider than the room it has. In a table that room is
    // the box the table sits in, not the cell: a table that squeezes its columns
    // makes every cell too narrow for its words, and that squeeze is the fault.
    // In a stacked table the cell is the whole row wide. Widths are measured on
    // a canvas in the word's font.
    const ruler = document.createElement("canvas").getContext("2d")!;
    for (const cell of document.querySelectorAll("td, th")) {
      const table = cell.closest("table");
      if (cell.getClientRects().length === 0 || !table?.parentElement) {
        continue;
      }
      const around = getComputedStyle(cell).display === "block" ? cell : table.parentElement;
      const box = getComputedStyle(around);
      const room = around.clientWidth - parseFloat(box.paddingLeft) - parseFloat(box.paddingRight);
      const texts = document.createTreeWalker(cell, NodeFilter.SHOW_TEXT);
      for (let node = texts.nextNode(); node; node = texts.nextNode()) {
        const font = getComputedStyle(node.parentElement!);
        ruler.font = `${font.fontStyle} ${font.fontWeight} ${font.fontSize} ${font.fontFamily}`;
        // Firefox adds an empty rectangle where a line ends just before a range.
        const lines = (from: number, to: number) => {
          const range = document.createRange();
          range.setStart(node!, from);
          range.setEnd(node!, to);
          return [...range.getClientRects()].filter((rect) => rect.width > 0.5);
        };
        for (const word of (node.textContent ?? "").matchAll(/\S+/g)) {
          const start = word.index!;
          const text = word[0];
          if (lines(start, start + text.length).length < 2 || ruler.measureText(text).width > room) {
            continue;
          }
          for (let i = 1; i < text.length; i++) {
            const before = lines(start + i - 1, start + i)[0];
            const after = lines(start + i, start + i + 1)[0];
            if (before && after && after.top > before.top + 1 && text[i - 1] !== "-") {
              found.push(`${JSON.stringify(text)} breaks after ${JSON.stringify(text.slice(0, i))}`
                + ` in a table with ${room.toFixed(0)} px of room`);
              break;
            }
          }
        }
      }
    }
    // A phone number in a table is read as one unit and keeps to one line,
    // although its spaces would let it break.
    for (const cell of document.querySelectorAll("td, th")) {
      const number = (cell.textContent ?? "").trim();
      if (cell.getClientRects().length === 0 || !/^\+?\d[\d -]{5,}\d$/.test(number)) {
        continue;
      }
      const range = document.createRange();
      range.selectNodeContents(cell);
      const rows = new Set([...range.getClientRects()].filter((r) => r.width > 0.5).map((r) => Math.round(r.top)));
      if (rows.size > 1) {
        found.push(`the phone number ${JSON.stringify(number)} breaks over ${rows.size} lines`);
      }
    }
    // A section menu wraps only when the column is too narrow for it.
    for (const list of document.querySelectorAll(".section-nav ul")) {
      const items = [...list.children].filter((item) => item.getClientRects().length > 0);
      const rows = new Set(items.map((item) => Math.round(item.getBoundingClientRect().top)));
      const column = list.closest("main");
      if (!column || rows.size < 2) {
        continue;
      }
      const style = getComputedStyle(column);
      const room = column.clientWidth - parseFloat(style.paddingLeft) - parseFloat(style.paddingRight);
      const gap = parseFloat(getComputedStyle(list).columnGap) || 0;
      const needed = items.reduce((sum, item) => sum + item.getBoundingClientRect().width, 0) + gap * (items.length - 1);
      if (needed <= room) {
        found.push(`the section menu takes ${rows.size} lines though it needs ${needed.toFixed(0)} px of ${room.toFixed(0)}`);
      }
    }
    // A field and the button on the same line beside it are the same height.
    for (const button of visible) {
      const r = button.getBoundingClientRect();
      for (const field of button.parentElement!.querySelectorAll<HTMLElement>(
        ":scope > input:not([type=hidden], [type=radio], [type=checkbox]), :scope > select")) {
        const f = field.getBoundingClientRect();
        if (field.getClientRects().length > 0 && f.top < r.bottom && r.top < f.bottom
            && Math.abs(f.height - r.height) > 1) {
          found.push(`a ${field.tagName.toLowerCase()} is ${f.height.toFixed(1)} px tall beside ${name(button)}`
            + ` at ${r.height.toFixed(1)} px`);
        }
      }
    }
    // A radio button or checkbox is at least 24 px square, the WCAG 2.2 target
    // size, and no wider than it looks: a stretched one draws its box in the
    // middle of a wide element, so the element's edge says nothing about where
    // the box is. It sits just before the first letter of its label, on the
    // same line.
    for (const input of document.querySelectorAll<HTMLInputElement>("input[type=radio], input[type=checkbox]")) {
      if (input.getClientRects().length === 0) {
        continue;
      }
      const size = input.getBoundingClientRect();
      if (size.width < 23.5 || size.height < 23.5 || size.width > 32) {
        found.push(`a ${input.type} is ${size.width.toFixed(0)} by ${size.height.toFixed(0)} px`);
        continue;
      }
      const label = input.labels?.[0];
      const texts = label ? document.createTreeWalker(label, NodeFilter.SHOW_TEXT) : null;
      let first: Node | null = null;
      for (let node = texts?.nextNode() ?? null; node && !first; node = texts!.nextNode()) {
        first = node.textContent?.trim() ? node : null;
      }
      if (!label || !first) {
        found.push(`a ${input.type} has no label text`);
        continue;
      }
      const letter = document.createRange();
      const at = first.textContent!.search(/\S/);
      letter.setStart(first, at);
      letter.setEnd(first, at + 1);
      const l = letter.getBoundingClientRect();
      const box = input.getBoundingClientRect();
      const gap = l.left - box.right;
      if (l.top >= box.bottom || box.top >= l.bottom || gap < 0 || gap > 24) {
        found.push(`the ${input.type} for ${JSON.stringify(label.textContent!.trim().slice(0, 40))}`
          + ` is not just before its text (${gap.toFixed(0)} px to the right, ${(l.top - box.top).toFixed(0)} px down)`);
      }
    }
    return found;
  });
}

/// What has to be on a page before it is checked, so the checks see what they
/// were written for: the register's widest address, and the checkboxes and
/// radio buttons of the mailing and shift forms.
const SHOWN: Record<string, string> = {
  "/admin/medlemmar": `tr:has-text("${WIDE_MEMBER.email}")`,
  "/admin/utskick": "input[type=checkbox], input[type=radio]",
  "/admin/volontarpass": "input[type=checkbox], input[type=radio]",
};

/// The problems on the page as it is now, each after the page's path.
async function here(page: Page, path: string): Promise<string[]> {
  return (await problems(page)).map((problem) => `${path}: ${problem}`);
}

for (const width of WIDTHS) {
  test.describe(`${width} px wide`, () => {
    test.use({ viewport: { width, height: 900 } });

    test("every public page has no layout problems", async ({ page }) => {
      const found = await scan(page, PUBLIC, problems);
      // The welcome page answers only the confirmation of a mailed link.
      const email = freshAddress("layout");
      await clearMail(email);
      await page.goto("/bli-medlem");
      await page.getByLabel(text("member.name"), { exact: true }).fill("Åsa Öberg");
      await page.getByLabel(text("member.email"), { exact: true }).fill(email);
      await page.getByRole("button", { name: text("application.submit") }).click();
      await expect(page).toHaveURL("/bli-medlem/skickat");
      await page.goto((await waitForMail(email)).link);
      await page.getByRole("button", { name: text("application.confirm.submit") }).click();
      await expect(page.getByRole("heading", { name: text("application.welcome.heading", "Åsa Öberg") }))
        .toBeVisible();
      await page.evaluate(() => document.fonts.ready);
      found.push(...await here(page, "/bli-medlem/bekrafta, welcome"));
      expect(found).toEqual([]);
    });

    // The passkey offer is on the first page after a login only.
    test("every member page has no layout problems", async ({ page }) => {
      const household = await householdPages();
      await linkLogin(page, "member", household.email);
      await expect(page.locator("[data-passkey-offer]")).toBeVisible();
      const found = await here(page, "/medlem");
      const offers = await details(page, "/medlem/erbjudanden", "/medlem/erbjudanden/");
      expect(offers).not.toEqual([]);
      expect([...found, ...await scan(page, [...MEMBER.filter((path) => path !== "/medlem"), ...offers, ...household.paths], problems, SHOWN)]).toEqual([]);
    });

    test("every administrator page has no layout problems", async ({ page }) => {
      await wideMember();
      await linkLogin(page, "administrator", ADMINISTRATORS.ada);
      await expect(page.locator("[data-passkey-offer]")).toBeVisible();
      const found = await here(page, "/admin");
      await page.goto("/admin/medlemmar");
      const unpaid = await page.getByRole("row").filter({ hasText: text("fee.notPaid") }).first()
        .getByRole("link").getAttribute("href");
      expect(unpaid).not.toBeNull();
      // An unpaid member's page has the radio buttons to record a payment.
      const shown = { ...SHOWN, [unpaid!]: "input[type=radio]" };
      const members = await details(page, "/admin/medlemmar", "/admin/medlemmar/");
      const offers = await details(page, "/admin/erbjudanden", "/admin/erbjudanden/");
      expect(offers).not.toEqual([]);
      // The list is newest first, and the seeded offer with registrations, the
      // only one with a table of who registered, is the oldest.
      const [registered] = await sql<{ id: string }>("SELECT min(offer_id) AS id FROM offer_registration");
      expect(registered.id, "an offer with registrations").not.toBeNull();
      const withTable = `/admin/erbjudanden/${registered.id}`;
      offers.push(withTable);
      shown[withTable] = "tbody tr";
      await prepareAdministratorPages(page);
      found.push(...await here(page, "/admin/utskick/forhandsgranska"));
      await saveMailingDraft(page);
      const shifts = await details(page, "/admin/volontarpass", "/admin/volontarpass/");
      const mailings = await details(page, "/admin/utskick", "/admin/utskick/");
      const paths = [...new Set([...ADMIN, unpaid!, ...members, ...offers, ...shifts, ...mailings,
        ...members.map((path) => path + "/ta-bort"), ...offers.map((path) => path + "/ta-bort")])];
      expect([...found, ...await scan(page, paths, problems, shown)]).toEqual([]);
    });
  });
}
