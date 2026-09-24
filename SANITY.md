# Sanity – Teater i Huskvarna

Detta är CMS-delen (publikt innehåll) av föreningens nya webbplats. Byggd enligt kraven i projektplanen 2026-09-21 (Klas Åkerskog, produktägare).

> **Viktigast att komma ihåg:** Sanity får **aldrig** innehålla personuppgifter (namn, e-post, telefon för medlemmar). All medlemsdata ligger i Spring Boot-appens PostgreSQL-databas. Sanity är enbart för publikt, redaktionellt innehåll.

---

## Vad som redan finns

- **Sanity-projekt skapat**: `teater-i-huskvarna`, dataset `production`
- **Studio + frontend scaffoldat** via `npm create sanity@latest` med Next.js-mallen:
  ```
  teater-i-huskvarna/
  ├── studio/   ← Sanity Studio (detta repo/mapp)
  └── web/      ← Next.js-appen som renderar sidan
  ```
- **Inloggning** kopplad till elena.developer26@gmail.com (byt till föreningens funktionsadress, se "Att tänka över" nedan)
- Tutorial-exempeldata (Movie/Person/Screening) har rensats bort

## Vad som ÅTERSTÅR att bygga (enligt krav R1)

Projektplanen kräver specifikt dessa **fem** innehållstyper i Sanity – bygg schemana efter denna lista, inte efter en filmtutorial eller annan struktur:

| Schema (`name`) | Svenskt namn | Krav-koppling |
|---|---|---|
| `evenemang` | Evenemang | P1, P2 – startsida visar nästa evenemang, kalender filtrerbar per serie |
| `nyhet` | Nyhet | P3 – nyhetslista och nyhetssida |
| `sida` | Sida | P4 – Om föreningen, Styrelsen, Produktioner, Ludde-priser, Kontakt |
| `erbjudande` | Erbjudande | M3 – medlemmar ska kunna se och anmäla sig, med antal platser kvar |
| `partner` | Partner | P4 – partnersida |

**Detaljer värda att bygga in redan i schemat:**
- `evenemang` behöver ett fält för **serie/kategori** (P2 nämner "Kaffe med drömmar", "Teaterträdgården Smedbyn", "Alf Henrikson-dagen" som exempel på filtrerbara serier) – gör det till en referens eller en `string`-lista, inte fritext, så filtreringen blir pålitlig.
- `erbjudande` behöver ett fält för **antal platser kvar** (M3) – men själva anmälningslogiken (vem som anmält sig) hör hemma i Spring Boot-databasen, inte i Sanity. Sanity äger bara texten/beskrivningen av erbjudandet.
- Alla typer bör ha ett fält som stödjer **förhandsgranskning innan publicering** (R3, bör-krav) – Sanitys drafts/published-flöde ger detta gratis, inget extra att bygga.

## Att tänka över innan ni bygger vidare

### TypeScript / compiler
- `sanity.config.ts` och schema-filerna är TypeScript – studenterna skriver alltså lite JS/TS även om huvudspåret är Java (medvetet avvägt i projektplanen).
- Kontrollera att `tsconfig.json` har `strict: true` påslaget från start – lättare att fånga fel i scheman tidigt än att lägga till strikt typning i efterhand.
- Sanitys egna typer (`defineField`, `defineType` från `sanity`-paketet) ger autocomplete och fångar felstavade fältnamn – använd dem konsekvent, skriv inte råa objekt-literaler för scheman.

### package.json / paketval
- Håll `sanity`- och `@sanity/vision`-paketen på samma major-version som varandra – blandade versioner är en vanlig källa till konstiga fel i Studio.
- `@sanity/vision` (GROQ-utforskaren) behövs bara i utveckling, inte i produktion om ni bygger en statisk export av Studio – men den är lätt nog att den sällan är värd att optimera bort.
- Lägg till `"engines"` i `package.json` som pekar på den Node-version ni faktiskt utvecklar mot, så nästa Lexicon-omgång (förvaltningsmodellens "förstahand") inte gissar fel Node-version.

### Miljövariabler
Dessa bör **aldrig** hårdkodas i schema-filer eller committas till Git:

| Variabel | Var den används | Kommentar |
|---|---|---|
| `SANITY_PROJECT_ID` | `sanity.config.ts`, Next.js-appen | Offentlig identifierare, men håll ändå i `.env` för att slippa hårdkodning |
| `SANITY_DATASET` | Samma | `production` idag – överväg ett separat `development`-dataset för testdata, se tips nedan |
| `SANITY_API_TOKEN` | Next.js-appens serverkod (för att läsa drafts, skriva via API) | **Hemlig.** Skapas i sanity.io/manage → API → Tokens. Ge den lägsta möjliga behörighet (Viewer räcker om appen bara läser publicerat innehåll) |
| `SANITY_WEBHOOK_SECRET` | Next.js-appens revalidate-endpoint | Verifierar att webhook-anrop faktiskt kommer från Sanity, inte någon annan |

Lägg alla i `.env.local` (redan gitignorad av Next.js-mallen som standard) – dubbelkolla att `.gitignore` faktiskt träffar filen innan första committen.

### Kontoägarskap (kopplat till projektplanens krav om förvaltning)
Enligt projektplanen ska **Sanity-projektet ägas av en funktionsadress** styrelsen kontrollerar (t.ex. `webb@teaterihuskvarna.se`), inte en students eller enskild ledamots privata Gmail. Byt ägarskap på projektet i **sanity.io/manage → Members** innan sprint 6 (överlämningen), annars blir det ett hinder vid förvaltarbytet.

---

## Tips: hålla sig inom gratisnivån (eller billigare alternativ)

Projektplanen bygger medvetet på Sanitys gratisplan i produktion. Källorna i planen är dock oense om exakta gränser (20 användare/10 000 dokument/1 miljon cachade anrop per månad, **eller** bara 2 användare utöver admin och 500 000 anrop) – **verifiera aktuella gränser på sanity.io/pricing innan lansering**, då villkor ändras.

Konkreta sätt att hålla nere förbrukningen:

1. **Cacha aggressivt, hämta sällan.** Systemskissen bygger redan på detta (Spring Boot-appen cachar Sanity-innehåll, webhook tömmer cachen bara vid publicering) – det är rätt mönster. Undvik att bygga något som frågar Sanity vid varje sidladdning.
2. **Ett dataset, inte flera, om ni ligger nära gränsen.** Fler dataset (t.ex. separat `development`) är bekvämt under utveckling men räknas troligen mot samma kvot – slå ihop till ett dataset inför produktion om ni närmar er gränsen, eller använd `development` bara lokalt och radera det innan lansering.
3. **Bilder: använd Sanitys inbyggda bildpipeline istället för egen bildoptimering.** Sanitys CDN skalar/beskär bilder automatiskt via URL-parametrar (`?w=800&h=600&fit=crop`) – ingen anledning att bygga egen bildhantering eller betala för en separat tjänst som Cloudinary.
4. **GROQ-frågor: hämta bara de fält ni faktiskt behöver**, inte hela dokumentet (`*[_type == "evenemang"]{titel, datum, slug}` istället för `*[_type == "evenemang"]`) – mindre data per anrop, snabbare sidor, färre anrop mot gränsen vid paginering.
5. **Håll antalet redaktörer/adminanvändare i Sanity Studio lågt** (bara de 2–3 redaktörer som faktiskt nämns i projektplanen) – fler användare kan vara det som faktiskt slår i den lägre gränsangivelsen (2 utöver admin) om den visar sig stämma.
6. **`@sanity/vision` och tunga devDependencies**: håll dem som `devDependencies`, aldrig `dependencies`, så de inte bloatar en eventuell produktionsbuild i onödan.
7. **Om ni ändå växer ur gratisplanen**: exportera innehållet (`sanity dataset export`) innan ni bestämmer er – det är alltid möjligt att flytta till en annan lösning, vilket gör det tryggt att stanna på gratisplanen så länge som möjligt utan att känna sig inlåsta.

---

## Kom igång lokalt

```bash
cd studio
npm install
npm run dev
```

Öppnar Studio på `http://localhost:3333`.

## Innan ni committar

- [ ] `.env.local` är gitignorad, inga nycklar i historiken
- [ ] Schema-namnen matchar tabellen ovan (`evenemang`, `nyhet`, `sida`, `erbjudande`, `partner`) – inte kvarvarande filmtutorial-namn
- [ ] Inga personuppgiftsfält (namn/e-post/telefon på enskilda medlemmar) finns i något schema