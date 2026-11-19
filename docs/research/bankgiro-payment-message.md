---
created: 2026-10-09
provenance: agent
description: Whether a member's email address fits in the message of a bankgiro payment, by length and by character set.
---

# An email address in a bankgiro payment message

Researched on 2026-10-09 for the payment guide in issue #43, which tells a member to write their account's email address as the message. Nothing found says a bank rejects `@`, and nothing says it accepts it. Length is the clearer risk until bankgiro payments move to the new infrastructure in autumn 2026.

## TL;DR

- Bankgirot's file format to the receiver has room: a 25-character reference field and up to 90 information records of 50 characters each. It types both as alphanumeric without listing which characters that includes.
- Banks cap the message far below that. A 2023 forum measurement of account transfers gives SEB 12 characters, Swedbank 35 on the web and 14 in the app, and Skandia 30. SEB confirms the 12 for transfers.
- The new ISO 20022 infrastructure allows 140 characters of payment information. Bankgiro payments move to it in autumn 2026.
- Whether `@` survives is not established. The SEPA basic Latin character set has no `@`, but no source found says the Swedish rules use that set.
- A test payment with an email address as the message, from more than one bank, settles length and `@` at once. That is C27 in [open-questions.md](../open-questions.md).

## Bankgirot's format to the receiver

[Bankgiro Inbetalningar, teknisk manual](https://www.bankgirot.se/globalassets/dokument/tekniska-manualer/bankgiroinbetalningar_tekniskmanual_sv.pdf), October 2023, describes the BgMax file a receiver gets:

- The payment record (transaction code 20) has `Referens`, 25 characters at positions 13-37. Without an OCR agreement, reference code 3 means the field holds one or more free references.
- The information record (transaction code 25) has `Informationstext`, 50 characters at positions 3-52, "Information från betalaren till Ditt företag". "Upp till 90 stycken informationsposter kan läggas till en betalning."
- Both fields have format `A`, which Table 1 defines only as "Alfanumeriskt innehåll". The manual gives no character set.

The manual does not say which of the two fields a private payer's message from an internet bank lands in. The association likely reads payments in its internet bank rather than in a BgMax file, so what the administrator sees is not established either.

## What banks let a payer type

A [SweClockers thread](https://www.sweclockers.com/forum/trad/1691491-bankernas-teckengranser-i-meddelandefalt) of 2023-06-03 measured the message field for transfers between accounts, not bankgiro payments:

| Bank | Web | App |
| --- | --- | --- |
| SEB | 12 | 12 |
| Swedbank | 35 | 14 |
| Skandia | 30 | 30, 12 in app versions 6.0.0 to 6.10.1 |

This is one user's measurement. [SEB](https://sebgroup.com/sv/vart-erbjudande/cash-management/nyheter-om-cash-management/ny-betalinfrastruktur-i-sverige) confirms the SEB figure: "Detta kan jämföras med 12 tecken för en kontoöverföring i det nuvarande formatet." No source found gives limits for bankgiro payments specifically, or for Handelsbanken and Nordea.

An address such as `anna.sjoberg@example.se` is 23 characters, so on these figures an SEB payer and a Swedbank app payer cannot type it in a transfer today.

## The new infrastructure

[SEB](https://sebgroup.com/sv/vart-erbjudande/cash-management/nyheter-om-cash-management/ny-betalinfrastruktur-i-sverige): "Meddelanden kan vara upp till 140 tecken långa (betalningsinformation)", and "Under hösten sker övergången av Bankgiro- och Plusgirobetalningar." [Kronofogden](https://kronofogden.se/om-kronofogden/nyheter-och-press/nyheter/2026-03-19-ny-infrastruktur-for-betalningar-till-och-fran-oss), 2026-03-19, gives the same 140 characters and autumn 2026.

No source found states the character set for payment information in the Swedish ISO 20022 rules. Bankgirot's message implementation guidelines would, and were not read.

## Agent notes

If `@` is dropped or a message is cut short, matching still mostly works: the administrators' register search matches any part of an account's email address, see `MemberRepository.search`. A message cut at 12 characters may match more than one member.
