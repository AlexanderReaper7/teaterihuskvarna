---
created: 2026-10-01
provenance: agent
description: How Swish can confirm membership payments automatically, with integration choices, prerequisites and published costs.
---

# Swish can confirm membership payments automatically

Researched on 2026-10-01 against Swish, banks and payment providers. The intended result is to confirm a payment and mark the membership fee paid automatically. Automatically charging next year's fee is a separate capability.

Swish Handel provides the needed payment request and confirmation API. A member still approves each ordinary payment with BankID. A QR code that only prefills a Swish Företag payment does not provide that confirmation API. [Nordea comparison](https://www.nordea.se/foretag/produkter/betala/swish-for-foretagare.html), [Swish integration guides](https://developer.swish.nu/documentation/guides).

The current [system plan](../projektplan.md#must-requirements-determine-whether-version-1-can-launch) excludes on-site payments and automatic bank reconciliation. R005 and R013 require payment instructions, and R019 requires manual fee marking in [requirements](../requirements.md). This research does not change that scope or choose a provider.

## The products automate different parts of payment

| Product | What it supplies | Fit for automatic fee confirmation |
| --- | --- | --- |
| Swish Företag | Receive payments to an association's Swish number without a checkout integration; printable QR codes can prefill payment details | A prefilled payment alone does not establish an application-created request with a server confirmation |
| Swish Handel | Create payment requests through an API and receive confirmation after the member approves | Matches the intended result |
| Swish Återkommande betalningar | Initiate payments under an active consent approved earlier by the payer | Relevant if automatic renewal becomes a requirement |
| Swish Utbetalning | Send money from an organisation to individuals | A separate product for payouts, not needed to collect membership fees |

Sources: [Nordea product comparison](https://www.nordea.se/foretag/produkter/betala/swish-for-foretagare.html), [Swish recurring payment guide](https://developer.swish.nu/documentation/guides/recurring-payments), [Swish payout guide](https://developer.swish.nu/documentation/guides/make-a-payout). Nordea explicitly offers Swish to associations as well as businesses. Eligibility and the bank agreement still need confirmation for this association. [Nordea association eligibility](https://www.nordea.se/foretag/produkter/betala/swish-for-foretagare.html).

## Swish Handel supplies a server confirmation

1. The backend creates a payment request with the amount, receiver, request identifier and HTTPS callback URL. Creation uses `PUT /api/v2/paymentrequests/{instructionUUID}`. HTTP `201` confirms request creation, not payment.
2. The member opens Swish and approves with BankID. An m-commerce request returns a token for launching Swish on the same phone or displaying a QR code on another screen. The e-commerce flow supplies `payerAlias`, the member's phone number, and the member opens Swish manually.
3. Swish posts the outcome to the callback URL. The outcomes include `PAID`, `DECLINED`, `ERROR` and `CANCELLED`. A status lookup can recover the result through `GET /api/v1/paymentrequests/{id}`.
4. The callback's `callbackIdentifier` header can be checked against the value supplied at creation. The guide recommends IP filtering. Failed callbacks receive up to 10 retries, starting after 5 seconds, then 10, 20, 40 and 60 seconds, until HTTP `200` or exhaustion.

Source: [Swish payment request guide](https://developer.swish.nu/documentation/guides/create-a-payment-request). The ordinary member approval uses BankID and transfers money directly to the merchant's bank account. [SEB payment flow](https://seb.se/foretag/digitala-tjanster/swish-foretag/swish-handel).

The API's commerce QR endpoint generates a QR code from a payment request token. Its separate prefilled endpoint generates a QR code with receiver, amount or message. These are different integrations despite both displaying a QR code. [Swish QR API](https://developer.swish.nu/api/qr-codes/v1).

Swish's changelog says v1 payment and refund creation endpoints were scheduled for decommissioning on 2026-01-01 and directs new integrations to v2. V1 GET and PATCH remain supported. Old integration examples need checking against that distinction. [Swish changelog](https://developer.swish.nu/changelog).

The payment API lists creation, retrieval by a known identifier and cancellation. It lists no transaction-history or payment-list operation. Reusing a creation identifier can return `RP09`, so a repeated PUT is not guaranteed to return the earlier success. Optional `payerSSN` does not require collecting a member's personal identity number. [Swish payment API reference](https://developer.swish.nu/api/payment-request/v2).

## A direct integration requires bank onboarding and certificate maintenance

The association signs a Swish Handel agreement with its bank, receives a Swish number linked to its account and names certificate contacts. With direct integration, a certificate contact uses BankID to manage certificates in Swish's portal. Outgoing API calls use mutual TLS, including a client certificate and verification of Swish's server certificate. The certificates expire and need renewal. [Nordea onboarding and security](https://www.nordea.se/foretag/produkter/betala/swish-handel.html).

A technical supplier can handle certificates instead. The bank connects the association's merchant number to the supplier's agreement. This still involves the bank; choosing a supplier does not remove merchant onboarding. [Swish technical supplier documentation](https://developer.swish.nu/documentation/technical-supplier).

Swish's guidelines give client TLS certificates a five-year lifetime and make the merchant responsible for new keys and certificates before expiry. The guidelines require payment requests to originate from the paying person's action. Automated reminders must not generate unsolicited payment requests. [Swish guidelines](https://developer.swish.nu/documentation/guidelines).

The production callback must be reachable over HTTPS on port 443 with a certificate trusted by a public certificate authority. The environment documentation requires TLS 1.2 or later and lists the production callback sender's hostname for filtering. Use the current environment documentation when configuring access. [Swish environments](https://developer.swish.nu/documentation/environments).

### Simulator, sandbox and production prove different things

- Merchant Swish Simulator, MSS, simulates API responses and callbacks with published test certificates. It is suitable for success and failure handling, but does not connect a real member's phone. MSS does not enforce every production check, including matching the payee alias to the certificate's common name.
- The separate sandbox supports the mobile flow with test Swish and test BankID apps, invented identities and no bank transfers. Access requires contacting Swish for device registration and a dedicated certificate.
- Production uses the real bank agreement and merchant credentials. Neither successful simulator testing nor sandbox testing proves that the production account, certificate or callback configuration works.

Source: [Swish environments](https://developer.swish.nu/documentation/environments). No API request, sandbox transaction or real payment was executed during this research.

## Direct integration and managed integration have different costs

Published prices checked on 2026-10-01 are examples, not a quote for the association. They exclude any other bank account package, development, support and provider charges unless stated.

| Option | Published fixed charge | Published payment charge | Other confirmed detail |
| --- | --- | --- | --- |
| Swedbank Swish Handel | 50 SEK/month | 3 SEK/payment | 1,000 SEK setup per number |
| Nordea Swish Handel | 720 SEK/year | 1.50 SEK + 0.3%, capped at 10 SEK | Setup currently waived; ordinary setup 500 SEK; refund 2 SEK |
| SEB Swish Handel | 60 SEK/month | 2.50 SEK/receipt | Full price list needed for remaining charges |
| Quickpay with Swish Handel | Supplier quote needed in addition to bank costs | Supplier quote needed in addition to bank costs | Can use Quickpay's supplier certificate; payments go directly to the merchant account |
| Stripe Swish | This Swish price page states no fixed Swish charge | 1% + 3 SEK, capped at 7 SEK | Access and eligibility need confirmation; settlement follows Stripe's payout schedule |

Sources: [Swedbank Handel pricing](https://www.swedbank.se/foretag/betala-och-ta-betalt/swish/swish-handel.html), [Nordea Handel pricing](https://www.nordea.se/foretag/produkter/betala/swish-handel.html), [SEB Handel pricing](https://seb.se/foretag/digitala-tjanster/swish-foretag/swish-handel), [Quickpay integration](https://quickpay.net/payment-methods/swish/), [Stripe Swedish pricing](https://stripe.com/se/pricing/local-payment-methods), [Stripe Swish documentation](https://docs.stripe.com/payments/swish).

Quickpay documents Swish Handel integration through its supplier agreement or the merchant's own certificate. It exposes payment callbacks and explicitly says its Swish integration does not support subscriptions. [Quickpay Swish documentation](https://quickpay.net/payment-methods/swish/).

Stripe documents immediate payment confirmation, hosted Checkout and payment APIs, but currently presents Swish access through an access request. Stripe appears as the payment recipient in Swish; the association's name appears in the message. Stripe's Swish integration does not support recurring payments. It therefore needs a separate eligibility and member-facing naming assessment. [Stripe Swish documentation](https://docs.stripe.com/payments/swish).

### Association offers do not all cover Swish Handel

The bank pages checked on 2026-10-01 show association offers, but no published general Swish Handel discount for an ordinary cultural nonprofit association. The product and eligibility conditions matter:

| Offer | Verified terms | What it establishes for this project |
| --- | --- | --- |
| Swedbank's general nonprofit package | 1,200 SEK/year for the bank package; 5,500 SEK to become a new bank customer. Swish Företag is discounted to 1.50 SEK per receipt or refund | A general association offer exists, but the published discount names Företag, not the Handel API needed here. [Swedbank nonprofit offer](https://www.swedbank.se/foretag/bli-kund/bli-foreningskund.html) |
| Sparbanken Spira's nonprofit package | 700 SEK/year with free Swish; the general association page's footnote identifies a focus on Västervik and Åtvidaberg municipalities | A published free nonprofit Swish offer exists. The nonprofit page links Swish Företag, so free Handel/API access and eligibility for this association remain unverified. [Spira association offer](https://www.sparbankenspira.se/foretag/foreningar), [Spira nonprofit package](https://www.sparbankenspira.se/foretag/foreningar/ideell-forening.html) |
| Swedbank's sports offer in cooperation with Riksidrottsförbundet | Swish Företag 1.10 SEK per receipt or refund and no annual Swish Företag fee; the bank package remains 1,200 SEK/year. New-bank-customer setup is waived for RF-affiliated sports associations | A restricted sports offer, not evidence of eligibility for this theatre association or a Handel discount. [Swedbank sports offer](https://www.swedbank.se/foretag/bli-kund/foretagspaket/erbjudande-till-idrottsforeningar.html) |
| Nordea's 90-konto fundraising rate | Handel receipts to a 90-konto are listed at 0 SEK per transaction. The page still lists the 720 SEK annual Handel charge | A fundraising-account rate, not a waiver for every nonprofit. The page does not establish coverage for this association's membership fees. [Nordea Handel rates](https://www.nordea.se/foretag/produkter/betala/swish-handel.html) |
| SEB | The Företag page explicitly says associations pay the same as businesses, 40 SEK/month and 2.50 SEK/transaction. The price list publishes Handel at 60 SEK/month and 2.50 SEK/receipt | No general nonprofit Handel concession appears in these published rates. [SEB association pricing](https://seb.se/foretag/digitala-tjanster/swish-foretag/swish-for-foretag), [SEB price list](https://seb.se/foretag/tjanster/aktuella-priser) |
| Handelsbanken | Företag 49 SEK/month and 1.95 SEK/transaction; Handel 85 SEK/month and 1.95 SEK/transaction | The checked product page publishes no separate nonprofit rate. A local quote could differ. [Handelsbanken Swish products](https://www.handelsbanken.se/sv/foretag/konton-betalningar/ta-betalt/swish-for-foretag) |

A 90-konto requires approval from Svensk Insamlingskontroll and its independent scrutiny of fundraising. Being an ideell förening does not itself supply that approval. No evidence here establishes that the association holds a 90-konto or belongs to RF. [Nordea 90-konto explanation and application](https://www.nordea.se/foretag/produkter/betala/90konton.html).

The bank-customer setup fees above are separate from Swish setup. Nordea also publishes 5,000 SEK for a new association to become a bank customer, and SEB publishes 5,000 SEK. The earlier annual payment examples exclude these bank charges. [Nordea bank onboarding](https://www.nordea.se/foretag/produkter/betala/foretagskonto.html), [SEB bank onboarding](https://seb.se/foretag/tjanster/aktuella-priser).

Stripe's nonprofit discount includes Sweden, but requires at least 80% of payment volume to be tax-deductible donations. It explicitly excludes membership fees, tickets and registration fees from eligible donations. Membership-fee collection alone therefore does not qualify, and the source establishes no discounted Swish rate. [Stripe nonprofit discount conditions](https://support.stripe.com/questions/fee-discount-for-nonprofit-organizations).

A theatre-specific example exists. Hallarnas Skådespelarlag states that SEB sponsors its Swish membership payments at no charge. The page does not identify the Swish product or promise the same terms to another association. It documents sponsorship, not a public Handel pricing policy. [Hallarnas membership payments](https://hallarnasteater.se/bli-medlem/).

### Annual examples depend on payments, not member count

The [first meeting](../meetings/2026-09-24-meeting-1.md#the-association-today) records 115 members and room to grow to 200. Household fees mean those numbers cannot be assumed to equal payment counts.

For comparison only, assuming 115 successful payments in a year:

- Swedbank Handel costs `12 × 50 + 115 × 3 = 945 SEK`, plus setup in the first year.
- Nordea Handel costs `720 + 115 × (1.50 + 0.003 × fee)`, or `909.75 SEK` for 50 SEK fees and `927 SEK` for 100 SEK fees, under the current waived setup offer.
- Stripe's displayed transaction price gives `402.50 SEK` for 115 payments of 50 SEK or `460 SEK` for 115 payments of 100 SEK. This is a transaction-fee illustration, not confirmation of merchant access or an all-in contract price.

At 200 payments, the corresponding amounts are 1,200 SEK for Swedbank, 1,050 to 1,080 SEK for Nordea and 700 to 800 SEK at Stripe's displayed transaction rate. These calculations use the prices above; they are not estimates of actual payment volume.

## Refunds and recurring charges need separate treatment

The Commerce API can refund a payment in full or part using its original payment reference, up to the original amount. Refunds are asynchronous; `DEBITED` means money left the merchant account and `PAID` means completion. The developer guide states a 13-month refund window, while Swedbank's current product page states 12 months. Confirm the applicable bank agreement instead of choosing one from these conflicting descriptions. [Swish refund guide](https://developer.swish.nu/documentation/guides/refund-a-payment), [Swedbank refund rules](https://www.swedbank.se/foretag/betala-och-ta-betalt/swish/swish-handel.html).

Recurring Swish exists today. It needs a recurring-payment agreement with the bank and an active payer consent. After the member approves the consent, the merchant triggers charges through single or batch APIs and receives results. Swish does not retry declined recurring payments; retry logic belongs to the merchant. [Swish recurring guide](https://developer.swish.nu/documentation/guides/recurring-payments).

Swedbank publishes 50 SEK/month per number, 3.50 SEK/payment and 1,000 SEK setup. The member approves the consent with BankID, then does not approve each charge individually. [Swedbank recurring payments](https://www.swedbank.se/foretag/betala-och-ta-betalt/swish/swish-aterkommande-betalningar.html). Ending consent stops automatic payments but does not itself decide whether membership ends. [Swish recurring communication guidance](https://www.swish.nu/marknadsmaterial/aterkommande-betalningar).

## Agent notes

The recommendation is an application-created Swish Handel payment request for the chosen membership fee, followed by automatic recording only after a verified `PAID` result. The member approves each payment. Recurring billing is unnecessary for the clarified request.

Direct integration is a reasonable candidate if an association maintainer can own certificate renewal and callback operations. A technical supplier is a reasonable candidate if outsourcing that work matters more than the added provider charge. Obtain both quotes from the association's existing bank before choosing; changing banks only to reduce a small fee may cost more administrative work than it saves.

For nonprofit pricing, ask the existing bank for a Swish Handel quote specifically for automated membership-fee confirmation, and ask whether a local sponsorship agreement can cover the fixed charge, setup and transaction fees. A free Företag offer does not settle the API requirement. Neither RF membership nor 90-konto fundraising status should be assumed.

An implementation proposal needs to preserve these properties before any schema or interface is chosen:

- Associate the request with the intended account, year, fee kind and expected amount before contacting Swish. The payer's phone number or name is not a reliable membership identifier; someone else may pay a household's fee.
- A created request, app return or displayed payment screen must not mark the fee paid. Check the server-confirmed result, receiver, amount, currency and request association.
- Duplicate callbacks and repeated status lookups must record one payment once. A timeout after sending a request must not create a second charge without resolving the first request.
- Persist enough information to recover an unfinished request after restart or missed callbacks. Status retrieval is useful for application-created requests; this research establishes no general account-history API for unrelated Swish or bankgiro payments.
- Treat an unexplained late payment, a second successful attempt or a refund as an explicit exception. Automatically recording incoming money must not silently decide how refunds affect membership coverage.
- Preserve the existing individual and household coverage rules in [0025](../decisions/0025-member-register-in-the-application.md) and the [glossary](../../GLOSSARY.md). Automatic payment records need an honest source of attribution instead of inventing an administrator who marked them.

The current [`FeeService.markPaid`](../../src/main/java/se/teaterihuskvarna/member/FeeService.java) takes an administrator identifier, chooses the current year and household when called, and its undo method deletes the fee row. [`Fee`](../../src/main/java/se/teaterihuskvarna/member/Fee.java) records the administrator as `markedBy`. A callback cannot reuse this manual operation unchanged without misrecording attribution or a request crossing New Year or a household move. Persisting the intended year and household, recording the payment source and deciding refund behavior need a separate design decision.

Swish recommends waiting for callbacks first, then starting status retrieval after 10 seconds with exponential delays and jitter. Its guidelines also discourage exposing a Commerce merchant number because this can invite payments outside the checkout. These details support recovery for known requests and retaining an explicit exception process for unrelated receipts. [Swish guidelines](https://developer.swish.nu/documentation/guidelines).

The remaining decisions are which bank and Swish agreement the association already has, direct integration versus supplier, ownership of certificate maintenance, and treatment of refunds or duplicate payments. Evidence still needed includes an association-specific quote and example payments made by someone other than the covered member. Those examples must test the fee matching rule before implementation, as the [system plan](../projektplan.md#the-member-model-records-only-data-the-confirmed-workflows-need) requires.

Verification before implementation should exercise successful, declined and cancelled payments, forged and duplicate callbacks, restart after request creation, missed callback recovery, amount mismatch and duplicate successful attempts. Live production verification would then require a small authorised payment and refund, separately from automated tests.

## Source limits

Swish's payment guide, API reference, environment documentation and guidelines were inspected in the live browser because the text reader only receives their JavaScript placeholder. Other sources were read directly or through indexed official-page text. No third-party tutorial is evidence for a protocol claim here. An association-specific price, supplier total costs and approval for Stripe access remain unverified. Published prices and provider availability can change after this research date.
