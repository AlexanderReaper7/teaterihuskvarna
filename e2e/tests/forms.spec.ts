// Every form says which of its text fields may be left empty, the same way
// everywhere, and calls one field by one name: decided in #180.
import { expect, test, type Page } from "@playwright/test";
import { linkLogin } from "../support/auth";
import { clearLinkRequests, resetAdministrators } from "../support/db";
import { ADMINISTRATORS } from "../support/site";
import { details, householdPages, scan } from "../support/pages";

test.beforeEach(async () => {
  await resetAdministrators();
  await clearLinkRequests();
});

const OPTIONAL = " (frivilligt)";

/// Every field on the page that breaks the rule, as readable sentences. A
/// text field is an input a person types into, or a textarea. A form with no
/// other control, such as the register's search, is left out: a form of one
/// field has nothing to tell apart. Radio buttons and lists count as controls,
/// so the fee form's amount beside its two kinds is checked.
async function problems(page: Page): Promise<string[]> {
  return page.evaluate((optional) => {
    const found: string[] = [];
    for (const form of document.querySelectorAll("main form")) {
      const fields = [...form.querySelectorAll<HTMLInputElement | HTMLTextAreaElement>(
        "input:not([type=hidden], [type=checkbox], [type=radio], [type=submit], [type=file]), textarea")]
        .filter((field) => field.labels && field.labels.length > 0);
      if (form.querySelectorAll("input:not([type=hidden], [type=submit]), textarea, select").length < 2) {
        continue;
      }
      for (const field of fields) {
        const label = field.labels![0].textContent!.trim().replace(/\s+/g, " ");
        if (field.required === label.endsWith(optional)) {
          found.push(`${JSON.stringify(label)} is ${field.required ? "required" : "optional"}`);
        }
        if (/^Adress\b/.test(label)) {
          found.push(`${JSON.stringify(label)} is called Gatuadress elsewhere`);
        }
        if ((label.match(/\(/g) ?? []).length > 1) {
          found.push(`${JSON.stringify(label)} has more than one pair of brackets`);
        }
      }
    }
    return found;
  }, OPTIONAL);
}

// Proves the check can fail, both ways, on the name and on the brackets, and
// that only a form of one control is left out.
test("the check reports an unmarked optional field, a marked required one, Adress and two brackets", async ({ page }) => {
  await page.setContent(`<main><form>
    <label for="a">Telefon</label><input id="a">
    <label for="b">Namn (frivilligt)</label><input id="b" required>
    <label for="c">Adress (frivilligt)</label><input id="c">
    <label for="d">När (datum och tid) (frivilligt)</label><input id="d">
  </form><form>
    <label><input type="radio" name="k"> Enskild</label>
    <label for="e">Belopp</label><input id="e">
  </form><form>
    <label for="f">Sök</label><input id="f">
  </form></main>`);
  expect(await problems(page)).toEqual([
    '"Telefon" is optional',
    '"Namn (frivilligt)" is required',
    '"Adress (frivilligt)" is called Gatuadress elsewhere',
    '"När (datum och tid) (frivilligt)" has more than one pair of brackets',
    '"Belopp" is optional',
  ]);
});

test("the join form follows the rule", async ({ page }) => {
  expect(await scan(page, ["/bli-medlem"], problems)).toEqual([]);
});

test("the member's forms follow the rule", async ({ page }) => {
  const household = await householdPages();
  await linkLogin(page, "member", household.email);
  expect(await scan(page, ["/medlem", "/medlem/kontaktuppgifter", "/medlem/hushall", ...household.paths], problems)).toEqual([]);
});

test("the administrators' forms follow the rule", async ({ page }) => {
  await linkLogin(page, "administrator", ADMINISTRATORS.ada);
  const member = (await details(page, "/admin/medlemmar", "/admin/medlemmar/"))[0];
  const offer = (await details(page, "/admin/erbjudanden", "/admin/erbjudanden/"))[0];
  expect(await scan(page, ["/admin", "/admin/medlemmar", "/admin/medlemmar/ny", member, "/admin/hushall",
    "/admin/erbjudanden/nytt", offer, "/admin/handlingar", "/admin/volontarpass", "/admin/utskick"], problems)).toEqual([]);
});
