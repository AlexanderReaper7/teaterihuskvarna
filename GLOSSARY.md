# Glossary

> When a name or meaning changes, this file must also change in the same commit.

Canonical terms are English because code and project documentation are English. Swedish quotations remain unchanged in the customer document and requirement text.

## People and roles

### Association

Föreningen Teater i Huskvarna, the organisation that owns the site, member register and production accounts.

### Visitor

A person using the public site without member access.

_Avoid:_ anonymous user, public user

### Member

A person recorded in the member register. A member may lack a paid fee for the current year.

_Avoid:_ user, contact, subscriber

### Membership

The association's recorded relationship with a member. Membership and fee status are separate facts.

_Avoid:_ subscription

### Paying member

A member covered by a paid individual fee or household fee for the stated year. The year matters because payment does not carry forward.

_Avoid:_ active member

### Editor

A person authorised to create and publish public content. Editor permission does not grant access to the member register.

_Avoid:_ administrator, content administrator

### Administrator

A person authorised to manage members, fees, registrations, volunteer bookings and mailing audiences. Administrator permission is separate from editor permission.

_Avoid:_ editor, webmaster

## Membership

### Member register

The association's authoritative collection of members, contact details, household membership and annual fee records.

_Avoid:_ mailing list, address book

### Household

A grouping used to let one household fee cover several members. A household is not itself a member.

_Avoid:_ family, family account

### Membership application

A visitor's request to enter the member register. A new application starts without a paid fee.

_Avoid:_ registration, sign-up

### Membership fee

An annual payment recorded against one member. Its kind determines whether it covers that member or the member's household. ([system plan](docs/projektplan.md#the-member-model-records-only-data-the-confirmed-workflows-need))

### Individual fee

A membership fee that covers only the member against whom the payment is recorded.

### Household fee

A membership fee that covers the payer and every member in the payer's household.

_Avoid:_ family fee

### Fee status

Whether a member is covered by a paid membership fee for a stated year.

_Avoid:_ membership status, active status

## Content and participation

### Public content

Content that a visitor may read without member access, including news, events and association pages.

### Event

A scheduled public activity listed on the site.

_Avoid:_ offer, performance when the event is not a performance

### Event series

A named grouping of related events, such as Kaffe med drömmar or Alf Henrikson-dagen.

_Avoid:_ category

### Offer

A member benefit or limited-capacity activity for which a member may register.

_Avoid:_ event, discount when the benefit is not a discount

### Offer capacity

The number of places an offer has. Stored in PostgreSQL, not in Sanity, so that taking a place and checking the limit happen in one transaction. See the member model section of [projektplan.md](docs/projektplan.md).

_Avoid:_ seats, slots, places left (that is the remainder, not the capacity)

### Offer registration

A member's reservation of a place in an offer.

_Avoid:_ membership application, booking

### Volunteer shift

A dated task for volunteers at a performance, such as cloakroom or serving work.

_Avoid:_ event, offer

### Volunteer booking

A member's reservation of one volunteer shift.

_Avoid:_ offer registration

## Communication

### Mailing audience

The members selected for one mailing by an explicit rule, such as all paying members or all volunteers.

_Avoid:_ mailing list, because a list may persist after membership data changes

### Mailing

One bulk message sent to a mailing audience. A login email is not a mailing.

_Avoid:_ login email, newsletter when the message is not a newsletter

## System

### Capability

One thing the system can do, such as looking a member up by email or recording a fee. A capability is a method on an application service, never logic inside a controller or a template. ([0014](docs/decisions/0014-one-service-layer-two-adapters.md))

_Avoid:_ feature, use case, endpoint

### Application service

The class that holds the capabilities for one part of the domain, and the only thing allowed to reach the database for it.

_Avoid:_ manager, business layer, backend

### Adapter

A way into the application services. There are two, and they are peers: the JTE pages, which call a service in process, and the REST API, which wraps the same method in HTTP. Anything one adapter can do, the other can.

_Avoid:_ frontend, backend, because both adapters are in the same deployable

## Delivery

### Requirement

An accepted behavior or result tracked in [requirements.md](docs/requirements.md).

### MUST

A requirement that version 1 must satisfy before launch.

### SHOULD

A planned requirement that may be cut before any MUST requirement if time runs out.

### COULD

An optional requirement that is cut before SHOULD requirements and may wait until a later release.

### Version 1

The first production release, due at the end of week 12 with every MUST requirement complete.

_Avoid:_ prototype, minimum viable product

## WIP

### Former member

Provisional. A member who has not renewed, with the exact transition and retention period still awaiting a board decision.

### Member-only offer detail

Provisional. Offer information that a visitor must not read, if the board decides that offers contain such information.
