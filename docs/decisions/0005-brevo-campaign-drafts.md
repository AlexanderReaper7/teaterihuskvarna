# 0005 — The app drafts mailings, Brevo sends them

2026-09-22

## Decision

Brevo stays, and mail keeps two separate jobs.

Login links are transactional mail, sent by the application over SMTP through `JavaMailSender`, against Mailpit locally and Brevo in production.

Mailings to members are campaigns. The application builds the HTML from CMS content and posts it to Brevo's [`createEmailCampaign`](https://developers.brevo.com/reference/create-email-campaign), which creates campaigns in **draft** status by default. An administrator then opens Brevo, reviews the draft in Brevo's own editor, and sends it from there.

The application does not contain a mailing editor.

## Why Brevo survived being reconsidered

Brevo was in the produktägare's document, so it was inherited rather than chosen. Checking it against the alternatives, it holds up, and for a better reason than the document gives ("gratisplan med 300 mejl/dag").

Brevo is a French company storing data on EU servers. That removes the transfer-basis paperwork a US provider forces under GDPR, which matters because this is the only external service that will hold member names and addresses. Sanity, or whichever CMS wins, gets none.

The alternatives have gone backwards. Mailchimp's free plan is now 250 subscribers and 500 emails a month, with automation removed in June 2025. MailerLite halved its free subscriber cap from 1,000 to 500 in September 2025; it is also EU-based, in Lithuania, so that was not the deciding factor. Brevo's free plan is 300 emails a day against up to 100,000 contacts.

Brevo also does transactional and campaigns in one account, which is unusual and is what makes the split above possible without a second vendor.

## Why the app does not send the mailings itself

Requirements U1 to U4 read as a specification for a mailing feature inside the admin view: pick an audience, preview, send, log. Building that literally means building a mail editor.

It would be a bad one. Brevo's editor has template management, previews across mail clients, a test send, bounce and complaint handling, unsubscribe management, and delivery statistics. Anything built here in twelve weeks is a worse version of all of that, and then it has to be maintained on a budget of a few hours a year.

Handing Brevo a draft keeps what the requirements actually wanted:

- U1, the mailing is based on CMS content, so the text is written once. The application still builds the HTML, so this is unchanged.
- U2, preview and test send to yourself. Brevo's, which is better than ours would be.
- U3, an unsubscribe link in every mailing. Brevo manages the unsubscribe list, which also means an unsubscribe is honoured across every future campaign rather than depending on our own bookkeeping being right. This is the one with legal consequences if it goes wrong.
- U4, a log of what was sent. Brevo's campaign history, with open and bounce data the application would not otherwise have.

The requirement text in [requirements.md](../requirements.md) is a verbatim quotation and does not change. What changes is which component satisfies it.

## What it costs

An administrator learns a second tool, and the send button lives somewhere other than the admin view. That is a real cost against "easy to edit for the customer" and it is the reason this was a decision rather than an obvious call.

Set against it: the association gets deliverability management, and a member who unsubscribes stays unsubscribed without anyone here writing that code correctly.

The audience selection stays in the application, because it is the only component that knows who has paid. It resolves the audience to a list and passes it to Brevo with the draft.

## What it costs to undo

The draft-creation call is one integration point. Building the editor later means building it from nothing, which is the same work as building it now, so nothing is foreclosed. Going the other way, from a homemade editor to Brevo drafts, would mean throwing work away.
