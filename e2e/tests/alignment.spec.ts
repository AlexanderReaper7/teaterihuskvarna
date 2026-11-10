// How things line up, on every page, from a phone's width to the user's own
// 2560 px view: a field and the button after it, a link or button with an icon
// and the words around it, an article's line length, the titles in a row of
// cards, and the footer under the page. layout.spec.ts checks what overflows,
// touches or breaks.
import { expect, test, type Page } from "@playwright/test";
import { linkLogin } from "../support/auth";
import { clearLinkRequests, resetAdministrators } from "../support/db";
import { ADMINISTRATORS } from "../support/site";
import { ADMIN, MEMBER, PUBLIC, confirmations, details, householdPages, linkPages, prepareAdministratorPages, saveMailingDraft, scan } from "../support/pages";

test.beforeEach(async () => {
  await resetAdministrators();
  await clearLinkRequests();
});

const WIDTHS = [390, 900, 2560];

/// Every misalignment on the page as it is now, as readable sentences.
async function misalignments(page: Page): Promise<string[]> {
  return page.evaluate(() => {
    const found: string[] = [];
    const words = (e: Node) => JSON.stringify((e.textContent ?? "").trim().replace(/\s+/g, " "));
    const shown = (e: Element) => e.getClientRects().length > 0;
    // A text node's boxes, one per line it takes. Firefox adds an empty one
    // where a line ends just before the node.
    const lines = (node: Node) => {
      const range = document.createRange();
      range.selectNodeContents(node);
      return [...range.getClientRects()].filter((r) => r.width > 0.5);
    };

    // A one-line field and the button after it are level when they share a
    // line, centred on each other, and apart when a narrow screen puts the
    // button below. layout.spec.ts checks that the two are the same height.
    for (const button of document.querySelectorAll("main button")) {
      if (!shown(button)) {
        continue;
      }
      const b = button.getBoundingClientRect();
      for (const field of button.parentElement!.querySelectorAll(
        ":scope > input:not([type=hidden], [type=radio], [type=checkbox]), :scope > select")) {
        const f = field.getBoundingClientRect();
        const kind = field.tagName.toLowerCase();
        if (!shown(field)) {
          continue;
        } else if (f.top < b.bottom && b.top < f.bottom) {
          const off = (b.top + b.bottom) / 2 - (f.top + f.bottom) / 2;
          if (Math.abs(off) > 1) {
            found.push(`${words(button)} is centred ${off.toFixed(1)} px below the ${kind} beside it`);
          }
        } else if (b.top - f.bottom < 4) {
          found.push(`${words(button)} is ${(b.top - f.bottom).toFixed(1)} px below its ${kind}`);
        }
      }
    }

    // A link or button with an icon, in a line of text, has its words on the
    // same baseline as the words around it.
    for (const control of document.querySelectorAll("main :is(a, button):has(> svg.icon)")) {
      const own = [...control.childNodes].find((n) => n.nodeType === Node.TEXT_NODE && n.textContent!.trim());
      if (!own || !shown(control)) {
        continue;
      }
      const mine = lines(own)[0];
      const around = [...control.parentElement!.childNodes]
        .filter((n) => n.nodeType === Node.TEXT_NODE && n.textContent!.trim())
        .flatMap(lines)
        .find((r) => r.top < mine.bottom && mine.top < r.bottom);
      if (around && Math.abs(around.bottom - mine.bottom) > 1) {
        found.push(`the words of ${words(control)} sit ${(around.bottom - mine.bottom).toFixed(1)} px above the text beside them`);
      }
    }

    // An article's text keeps to the measure every other page has.
    const root = getComputedStyle(document.documentElement);
    const measure = parseFloat(root.getPropertyValue("--width-text")) * parseFloat(root.fontSize);
    const wide = [...document.querySelectorAll("main > article > p")].find((p) => p.getBoundingClientRect().width > measure + 0.5);
    if (wide) {
      found.push(`the article's text is ${wide.getBoundingClientRect().width.toFixed(0)} px wide, past the ${measure.toFixed(0)} px measure`);
    }

    // The titles of the cards in one row start at the same height, and a row
    // in which no card has a picture leaves no room for one above its text.
    for (const list of document.querySelectorAll(".cards")) {
      const rows = new Map<number, { titles: number[]; pictures: number; above: number }>();
      for (const card of list.querySelectorAll(":scope > .card")) {
        const title = card.querySelector("h2, h3");
        const text = card.querySelector(":scope > .card-text");
        if (!title || !text || !shown(card)) {
          continue;
        }
        const top = card.getBoundingClientRect().top;
        const row = rows.get(Math.round(top)) ?? { titles: [], pictures: 0, above: 0 };
        row.titles.push(title.getBoundingClientRect().top - top);
        row.pictures += card.querySelector(":scope > .card-picture") ? 1 : 0;
        row.above = Math.max(row.above,
          text.getBoundingClientRect().top - top - parseFloat(getComputedStyle(card).paddingTop));
        rows.set(Math.round(top), row);
      }
      for (const row of rows.values()) {
        if (Math.max(...row.titles) - Math.min(...row.titles) > 1) {
          found.push(`the card titles in one row start ${row.titles.map((o) => o.toFixed(0)).join(", ")} px down their cards`);
        }
        if (row.pictures === 0 && row.above > 1) {
          found.push(`a row of cards without pictures has ${row.above.toFixed(0)} px of nothing above its text`);
        }
      }
    }

    // The footer's text starts where the page's content does, also on an
    // administrator's page, which is wider than the others.
    const inside = (e: Element) => e.getBoundingClientRect().left + parseFloat(getComputedStyle(e).paddingLeft);
    const main = document.querySelector("main");
    const footer = document.querySelector(".site-footer p");
    if (main && footer && Math.abs(inside(footer) - inside(main)) > 1) {
      found.push(`the footer's text starts ${(inside(footer) - inside(main)).toFixed(0)} px to the right of the page's content`);
    }
    return found;
  });
}

for (const width of WIDTHS) {
  test.describe(`${width} px wide`, () => {
    test.use({ viewport: { width, height: 900 } });

    test("every public page lines up", async ({ page }) => {
      const links = await linkPages();
      expect(await scan(page, [...PUBLIC, ...links.paths], misalignments, links.shown)).toEqual([]);
    });

    test("every member page lines up", async ({ page }) => {
      const household = await householdPages();
      await linkLogin(page, "member", household.email);
      const offers = await details(page, "/medlem/erbjudanden", "/medlem/erbjudanden/");
      expect(offers).not.toEqual([]);
      expect(await scan(page, [...MEMBER, ...offers, ...household.paths], misalignments)).toEqual([]);
    });

    test("every administrator page lines up", async ({ page }) => {
      await linkLogin(page, "administrator", ADMINISTRATORS.ada);
      const members = await details(page, "/admin/medlemmar", "/admin/medlemmar/");
      const offers = await details(page, "/admin/erbjudanden", "/admin/erbjudanden/");
      expect(members).not.toEqual([]);
      expect(offers).not.toEqual([]);
      await prepareAdministratorPages(page);
      const preview = await misalignments(page);
      await saveMailingDraft(page);
      const shifts = await details(page, "/admin/volontarpass", "/admin/volontarpass/");
      const mailings = await details(page, "/admin/utskick", "/admin/utskick/");
      expect([...preview, ...await scan(page, [...ADMIN, ...members, ...offers, ...shifts, ...mailings,
        ...members.map((path) => path + "/ta-bort"), ...offers.map((path) => path + "/ta-bort"),
        ...await confirmations(page, shifts)], misalignments)]).toEqual([]);
    });
  });
}

// An article's main picture is 1200 px wide in its HTML, the width Sanity is
// asked for, and the fixture content has none, so each kind of article gets one
// where its template puts it. It may take the column, and no more.
for (const width of [390, 2560]) {
  test.describe(`an article's main picture at ${width} px`, () => {
    test.use({ viewport: { width, height: 900 } });

    for (const path of ["/om-foreningen", "/nyheter/ny-webbplats", "/evenemang/kulturnatten"]) {
      test(`${path} keeps it inside the column`, async ({ page }) => {
        await page.goto(path);
        const found = await page.locator("main > article").evaluate(async (article) => {
          const img = document.createElement("img");
          img.className = "hero";
          img.width = 1200;
          img.height = 800;
          img.alt = "";
          img.src = `data:image/svg+xml,${encodeURIComponent(
            '<svg xmlns="http://www.w3.org/2000/svg" width="1200" height="800"><rect width="1200" height="800" fill="teal"/></svg>')}`;
          [...article.querySelectorAll(":scope > h1, :scope > .meta")].pop()!.after(img);
          await img.decode();
          const style = getComputedStyle(article);
          const box = article.getBoundingClientRect();
          const right = box.right - parseFloat(style.paddingRight);
          const root = document.documentElement;
          return [
            ...(root.scrollWidth > root.clientWidth ? [`the page scrolls sideways: ${root.scrollWidth} px in ${root.clientWidth}`] : []),
            ...(img.getBoundingClientRect().right > right + 0.5
              ? [`the picture ends at ${img.getBoundingClientRect().right.toFixed(0)} px, past the column's ${right.toFixed(0)}`] : []),
          ];
        });
        expect(found).toEqual([]);
      });
    }
  });
}

// The fixture content has no pictures, so these pages get some, in the box
// tag/card.jte puts a card's picture in: an upright one, a square one and a
// wide one in turn. The last card of a list gets none of its own, only one in
// its text, as Portable Text writes a picture. Beside other cards it must keep
// the room of their pictures, so its title starts where theirs do. Alone in a
// row it must have no empty box.
//
// The fixture's lists hold two or three cards. At 1440 px each is one row, so
// the last card is beside the others. At 900 px a list of three is two rows,
// and its last card is alone. The test fails if a width stops giving the rows
// it is here for.
for (const { width, alone } of [{ width: 1440, alone: false }, { width: 900, alone: true }]) {
  test.describe(`cards with pictures of any proportions, ${width} px wide`, () => {
    test.use({ viewport: { width, height: 900 } });

    test(alone ? "leave no empty box in a row without pictures" : "line up their titles in a row", async ({ page }) => {
      const found: string[] = [];
      const rows = { mixed: 0, bare: 0 };
      for (const path of ["/", "/kalender", "/nyheter", "/styrelsen", "/produktioner"]) {
        await page.goto(path);
        const here = await page.locator(".cards").evaluateAll(async (lists) => {
          const loads: Promise<void>[] = [];
          for (const list of lists) {
            const cards = [...list.querySelectorAll(":scope > .card")];
            cards.slice(0, -1).forEach((card, i) => {
              const [w, h] = [[300, 420], [300, 300], [300, 140]][i % 3];
              const img = document.createElement("img");
              img.width = w;
              img.height = h;
              img.alt = "";
              img.src = `data:image/svg+xml,${encodeURIComponent(
                `<svg xmlns="http://www.w3.org/2000/svg" width="${w}" height="${h}"><rect width="${w}" height="${h}" fill="teal"/></svg>`)}`;
              const box = document.createElement("div");
              box.className = "card-picture";
              box.append(img);
              card.prepend(box);
              loads.push(img.decode());
            });
            const inText = document.createElement("figure");
            inText.innerHTML = `<img alt="" width="300" height="140" src="data:image/svg+xml,${encodeURIComponent(
              '<svg xmlns="http://www.w3.org/2000/svg" width="300" height="140"><rect width="300" height="140" fill="teal"/></svg>')}">`;
            cards[cards.length - 1]?.querySelector(":scope > .card-text")?.append(inText);
          }
          await Promise.all(loads);
          // The rows as they are laid out now, in the lists that got a picture.
          let mixed = 0;
          let bare = 0;
          for (const list of lists.filter((l) => l.querySelector(":scope > .card > .card-picture"))) {
            const pictures = new Map<number, boolean[]>();
            for (const card of list.querySelectorAll(":scope > .card")) {
              const top = Math.round(card.getBoundingClientRect().top);
              pictures.set(top, [...(pictures.get(top) ?? []), card.querySelector(":scope > .card-picture") !== null]);
            }
            for (const row of pictures.values()) {
              mixed += row.includes(true) && row.includes(false) ? 1 : 0;
              bare += row.includes(true) ? 0 : 1;
            }
          }
          return { pictured: loads.length, mixed, bare };
        });
        expect(here.pictured, `cards to put pictures in on ${path}`).toBeGreaterThan(0);
        rows.mixed += here.mixed;
        rows.bare += here.bare;
        found.push(...(await misalignments(page)).map((problem) => `${path}: ${problem}`));
      }
      expect(found).toEqual([]);
      if (alone) {
        expect(rows.bare, "rows without pictures in a list that has some").toBeGreaterThan(0);
      } else {
        expect(rows.mixed, "rows where a card without a picture is beside one with").toBeGreaterThan(0);
      }
    });
  });
}
