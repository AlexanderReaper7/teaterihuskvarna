---
created: 2026-09-21
provenance: user
description: Which features the system has to have, and at which priority.
---

# Requirements

Working copy of the requirement table in [projektplan-original.md](projektplan-original.md). That file is a frozen conversion of the produktägare's docx. Its ids, R001 to R025, are in the same order as the R ids here, so R001 is R001 and R025 is R025. An id is never reused, and a new requirement takes the next number. An id may be followed directly by its issue on the [board](https://github.com/users/AlexanderReaper7/projects/1), as in `R005 #8`. What the finished system has to be is in [projektplan.md](projektplan.md).

Priority: **MUST** has to be in version 1, **SHOULD** ought to be, **COULD** can wait for phase 2. The produktägare's document writes these as M, B and K, and [projektplan-original.en.md](projektplan-original.en.md) keeps those letters so the two files line up. M is MUST, B is SHOULD, K is COULD.

Requirement text is quoted verbatim in Swedish. A quotation does not get translated, and the produktägare has to be able to recognise his own wording. Everything around it is English, per [decisions/0001](decisions/0001-language-policy.md): the headings, the area labels and the priority values are all structural rather than quoted.

| Id | Area | Requirement | Priority |
| --- | --- | --- | --- |
| R001 #4 | Public site | Startsidan visar nästa evenemang överst | MUST |
| R002 #5 | Public site | Kalender med evenemang, filtrerbar per serie (Kaffe med drömmar, Teaterträdgården Smedbyn, Alf Henrikson-dagen m.fl.) | MUST |
| R003 #6 | Public site | Nyhetslista och nyhetssida | MUST |
| R004 #7 | Public site | Sidor för Om föreningen, Styrelsen, Partners, Produktioner, Ludde-priser, Kontakt | MUST |
| R005 #8 | Public site | Bli medlem-formulär som skapar en medlem med status “ej betald” och visar betalinstruktion | MUST |
| R006 #9 | Public site | Fungerar på mobil, tillgänglighet enligt WCAG 2.1 AA | MUST |
| R007 #10 | Editing | Innehållstyperna Evenemang, Nyhet, Sida, Erbjudande, Partner i Sanity | MUST |
| R008 #11 | Editing | Publicerat innehåll syns på sidan inom en minut | MUST |
| R009 #12 | Editing | Förhandsgranskning innan publicering | SHOULD |
| R010 #13 | Login | Lösenordsfri inloggning via engångslänk i e-post | MUST |
| R011 #14 | Login | Två roller: medlem och administratör | MUST |
| R012 #15 | Member pages | Visa och uppdatera egna kontaktuppgifter | MUST |
| R013 #16 | Member pages | Se medlemsstatus och betalinstruktion för årets avgift | MUST |
| R014 #17 | Member pages | Se erbjudanden och anmäla sig, med antal platser kvar | MUST |
| R015 #18 | Member pages | Årsmöteshandlingar och medlemsbrev | SHOULD |
| R016 #19 | Volunteers | Boka pass för garderob och servering per föreställning | MUST |
| R017 #20 | Volunteers | Påminnelse via e-post dagen innan passet | COULD |
| R018 #21 | Admin | Söka, lägga till, ändra och ta bort medlemmar | MUST |
| R019 #22 | Admin | Markera avgift som betald, hantera familjemedlemskap | MUST |
| R020 #23 | Admin | Se och exportera anmälningar per erbjudande och volontärpass | MUST |
| R021 #24 | Admin | Exportera medlemsregister som CSV | MUST |
| R022 #25 | Mailings | Skicka utskick till vald målgrupp, baserat på innehåll från Sanity | MUST |
| R023 #26 | Mailings | Förhandsgranska och testskicka till sig själv | MUST |
| R024 #27 | Mailings | Avregistreringslänk i varje utskick | MUST |
| R025 #28 | Mailings | Logg över skickade utskick | SHOULD |

Totals: 21 MUST, 3 SHOULD, 1 COULD.

R016 is MUST although its row in the original, V1, says B. The original's scope table lists "Volontärbokning för garderob och servering" under "I version 1", and where the document contradicts itself, the scope table wins (decided 2026-09-23). [`docs/check.py`](check.py) holds the same exception, so the two cannot drift apart.

R007 names Erbjudande as a Sanity type, but offers live in the application instead, because offer details are for members only and a dataset on Sanity's free plan publishes every published document. The quote stays as the produktägare wrote it, and the exception is in [decisions/0022](decisions/0022-offers-and-documents-in-the-application.md). Decided by the user on 2026-09-28.

If scope has to be cut, COULD goes first and then SHOULD. That is the produktägare's own rule and it is the reason this column exists.
