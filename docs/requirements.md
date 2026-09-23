# Requirements

Working copy of the requirement table in [projektplan-original.md](projektplan-original.md). That file is a frozen conversion of the produktägare's docx; this one is the list that gets edited as work proceeds. What the finished system has to be is in [projektplan.md](projektplan.md).

Priority: **MUST** has to be in version 1, **SHOULD** ought to be, **COULD** can wait for phase 2. The produktägare's document writes these as M, B and K, and [projektplan-original.en.md](projektplan-original.en.md) keeps those letters so the two files line up. M is MUST, B is SHOULD, K is COULD.

Requirement text is quoted verbatim in Swedish. A quotation does not get translated, and the produktägare has to be able to recognise his own wording. Everything around it is English, per [decisions/0001](decisions/0001-language-policy.md): the headings, the area labels and the priority values are all structural rather than quoted.

| Id | Area | Requirement | Priority | Status |
| --- | --- | --- | --- | --- |
| P1 | Public site | Startsidan visar nästa evenemang överst | MUST | Not started |
| P2 | Public site | Kalender med evenemang, filtrerbar per serie (Kaffe med drömmar, Teaterträdgården Smedbyn, Alf Henrikson-dagen m.fl.) | MUST | Not started |
| P3 | Public site | Nyhetslista och nyhetssida | MUST | Not started |
| P4 | Public site | Sidor för Om föreningen, Styrelsen, Partners, Produktioner, Ludde-priser, Kontakt | MUST | Not started |
| P5 | Public site | Bli medlem-formulär som skapar en medlem med status “ej betald” och visar betalinstruktion | MUST | Not started |
| P6 | Public site | Fungerar på mobil, tillgänglighet enligt WCAG 2.1 AA | MUST | Not started |
| R1 | Editing | Innehållstyperna Evenemang, Nyhet, Sida, Erbjudande, Partner i Sanity | MUST | Not started |
| R2 | Editing | Publicerat innehåll syns på sidan inom en minut | MUST | Not started |
| R3 | Editing | Förhandsgranskning innan publicering | SHOULD | Not started |
| I1 | Login | Lösenordsfri inloggning via engångslänk i e-post | MUST | Not started |
| I2 | Login | Två roller: medlem och administratör | MUST | Not started |
| M1 | Member pages | Visa och uppdatera egna kontaktuppgifter | MUST | Not started |
| M2 | Member pages | Se medlemsstatus och betalinstruktion för årets avgift | MUST | Not started |
| M3 | Member pages | Se erbjudanden och anmäla sig, med antal platser kvar | MUST | Not started |
| M4 | Member pages | Årsmöteshandlingar och medlemsbrev | SHOULD | Not started |
| V1 | Volunteers | Boka pass för garderob och servering per föreställning | MUST | Not started |
| V2 | Volunteers | Påminnelse via e-post dagen innan passet | COULD | Not started |
| A1 | Admin | Söka, lägga till, ändra och ta bort medlemmar | MUST | Not started |
| A2 | Admin | Markera avgift som betald, hantera familjemedlemskap | MUST | Not started |
| A3 | Admin | Se och exportera anmälningar per erbjudande och volontärpass | MUST | Not started |
| A4 | Admin | Exportera medlemsregister som CSV | MUST | Not started |
| U1 | Mailings | Skicka utskick till vald målgrupp, baserat på innehåll från Sanity | MUST | Not started |
| U2 | Mailings | Förhandsgranska och testskicka till sig själv | MUST | Not started |
| U3 | Mailings | Avregistreringslänk i varje utskick | MUST | Not started |
| U4 | Mailings | Logg över skickade utskick | SHOULD | Not started |

Totals: 21 MUST, 3 SHOULD, 1 COULD.

V1 is MUST although the original row says B. The original's scope table lists "Volontärbokning för garderob och servering" under "I version 1", and where the document contradicts itself, the scope table wins (decided 2026-09-23). `docs/check.py` holds the same exception, so the two cannot drift apart.

If scope has to be cut, COULD goes first and then SHOULD. That is the produktägare's own rule and it is the reason this column exists.
