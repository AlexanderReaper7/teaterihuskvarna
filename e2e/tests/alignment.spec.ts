// How things line up, on every page, from a phone's width to the user's own
// 2560 px view: a field and the button after it, a link or button with an icon
// and the words around it, an article's line length, and the titles in a row
// of cards. layout.spec.ts checks what overflows, touches or breaks.
import { expect, test, type Page } from "@playwright/test";
import { linkLogin } from "../support/auth";
import { clearLinkRequests, resetAdministrators } from "../support/db";
import { ADMINISTRATORS, MEMBERS, PATHS } from "../support/site";

test.beforeEach(async () => {
  await resetAdministrators();
  await clearLinkRequests();
});

const WIDTHS = [390, 900, 2560];

const PUBLIC = ["/", "/kalender", "/evenemang/kulturnatten", "/nyheter", "/nyheter/ny-webbplats", "/om-foreningen",
  "/styrelsen", "/produktioner", "/ludde-priser", "/kontakt", "/partners", "/bli-medlem", PATHS.member.login,
  PATHS.administrator.login, "/finns-inte"];
const MEMBER = ["/medlem", "/medlem/kontaktuppgifter", "/medlem/erbjudanden", "/medlem/handlingar", "/medlem/volontar",
  "/medlem/hushall"];
const ADMIN = ["/admin", "/admin/medlemmar", "/admin/medlemmar/ny", "/admin/hushall", "/admin/erbjudanden",
  "/admin/erbjudanden/nytt", "/admin/handlingar", "/admin/volontarpass", "/admin/utskick"];

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

    // The titles of the cards in one row start at the same height.
    for (const list of document.querySelectorAll(".cards")) {
      const rows = new Map<number, number[]>();
      for (const card of list.querySelectorAll(":scope > .card")) {
        const title = card.querySelector("h2, h3");
        if (!title || !shown(card)) {
          continue;
        }
        const top = card.getBoundingClientRect().top;
        rows.set(Math.round(top), [...(rows.get(Math.round(top)) ?? []), title.getBoundingClientRect().top - top]);
      }
      for (const offsets of rows.values()) {
        if (Math.max(...offsets) - Math.min(...offsets) > 1) {
          found.push(`the card titles in one row start ${offsets.map((o) => o.toFixed(0)).join(", ")} px down their cards`);
        }
      }
    }
    return found;
  });
}

/// The pages behind the first two links on `list` that lead to `prefix` and a
/// number, such as an offer's page from the list of offers.
async function details(page: Page, list: string, prefix: string): Promise<string[]> {
  await page.goto(list);
  const hrefs = await page.locator(`a[href^="${prefix}"]`).evaluateAll(
    (links, p) => links.map((a) => a.getAttribute("href") ?? "").filter((h) => new RegExp(`^${p}\\d+$`).test(h)),
    prefix);
  return [...new Set(hrefs)].slice(0, 2);
}

async function scan(page: Page, paths: string[]): Promise<string[]> {
  const found: string[] = [];
  for (const path of paths) {
    await page.goto(path);
    found.push(...(await misalignments(page)).map((problem) => `${path}: ${problem}`));
  }
  return found;
}

for (const width of WIDTHS) {
  test.describe(`${width} px wide`, () => {
    test.use({ viewport: { width, height: 900 } });

    test("every public page lines up", async ({ page }) => {
      expect(await scan(page, PUBLIC)).toEqual([]);
    });

    test("every member page lines up", async ({ page }) => {
      await linkLogin(page, "member", MEMBERS.erik);
      const offers = await details(page, "/medlem/erbjudanden", "/medlem/erbjudanden/");
      expect(offers).not.toEqual([]);
      expect(await scan(page, [...MEMBER, ...offers])).toEqual([]);
    });

    test("every administrator page lines up", async ({ page }) => {
      await linkLogin(page, "administrator", ADMINISTRATORS.ada);
      const members = await details(page, "/admin/medlemmar", "/admin/medlemmar/");
      const offers = await details(page, "/admin/erbjudanden", "/admin/erbjudanden/");
      expect(members).not.toEqual([]);
      expect(offers).not.toEqual([]);
      // A fresh stack has no shifts or mailings; other specs add them.
      const shifts = await details(page, "/admin/volontarpass", "/admin/volontarpass/");
      const mailings = await details(page, "/admin/utskick", "/admin/utskick/");
      expect(await scan(page, [...ADMIN, ...members, ...offers, ...shifts, ...mailings])).toEqual([]);
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

// The fixture content has no pictures, so these pages get some: an upright
// one, a square one and a wide one in turn, and none in the last card.
test.describe("cards with pictures of any proportions", () => {
  test.use({ viewport: { width: 1440, height: 900 } });

  for (const path of ["/", "/kalender", "/nyheter", "/styrelsen", "/produktioner"]) {
    test(`${path} lines up its card titles`, async ({ page }) => {
      await page.goto(path);
      const pictured = await page.locator(".cards").evaluateAll(async (lists) => {
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
            card.prepend(img);
            loads.push(img.decode());
          });
        }
        await Promise.all(loads);
        return loads.length;
      });
      expect(pictured, "cards to put pictures in").toBeGreaterThan(0);
      expect(await misalignments(page)).toEqual([]);
    });
  }
});
