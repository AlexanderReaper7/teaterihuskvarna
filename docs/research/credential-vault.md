---
created: 2026-11-11
provenance: reviewed
description: Where the association keeps the logins and keys for its production accounts, for free, so that two board members and a maintainer can reach them and neither a person leaving nor a lost server locks the association out.
---

# A self-hosted Vaultwarden for the association's credentials

Researched on 2026-11-11 from Bitwarden's and Vaultwarden's documentation and one security paper, in a conversation with the user. Nothing was installed or run. Issue #265 tracks the setup.

## TL;DR

- The plan needs the association's production credentials to reach two board members (C3) and a maintainer (C2), and to survive both one of them leaving and the loss of the server.
- The user set three constraints on 2026-11-11: it must be free, the site updates itself automatically, and nobody gets a recurring technical task.
- Bitwarden's free cloud organization holds two users, one short of the three people. Its Teams plan costs $4 per user per month. Neither fits.
- Vaultwarden is a free server that the official Bitwarden apps work with, with no user limit, and it includes organizations, emergency access and event logs. The agent recommends it, on the production server, configured once in the compose file.
- Board members only use the Bitwarden app. The maintainer invites and removes people. An offline copy of the backup key and the hosting login means the vault is not the only way back after losing the server.
- The risk that remains: whoever takes over the server can change the web vault's login page and catch the master password of the next person who logs in there. People who use the apps are not exposed to that.

## What the plan requires

From [the system plan](../projektplan.md):

- "Production accounts belong to the association and use an association-controlled function address. At least two board members have administrator access."
- One release condition is that "the named maintainer has received the runbook and credentials".
- Two of the seven checks: "The register survives one person leaving", checked by a second named board member signing in to every production account, and "The register survives loss of the host", checked by restoring a production-format backup into an empty database.
- The daily backup goes to encrypted storage on another machine ([0007](../decisions/0007-postgres-in-a-container.md)). Its key has to exist somewhere off the server, or the backup cannot be restored after the server is lost.

So three people need the credentials: two board members and the maintainer.

## The user's constraints

On 2026-11-11 the user wrote:

> It must be free.

> There will be a maintainer, and we should make the site auto update regardless.

> Toggling on and off the admin page and other technical knowledge requirements are too much.

The last one answered the agent's suggestion to keep Vaultwarden's web vault switched off except while someone uses it, see [the remaining risk](#the-remaining-risk-the-web-vault-on-a-shared-server).

## Options

| Option | Cost | People | What it takes |
| --- | --- | --- | --- |
| Bitwarden cloud, free organization | Free | 2 | Nothing to run |
| Bitwarden cloud, Teams | $4 per user per month, billed annually | No limit | Nothing to run |
| Vaultwarden, self-hosted | Free | No limit | A server, updates and backups |
| A KeePassXC file on shared storage | Free | No limit | Storage the association controls |

### Bitwarden cloud

- A free organization is for sharing with "one other user", so two users, and holds "up to two collections". Emergency access is a premium feature, which only paid organizations give their members ([Password Manager plans](https://bitwarden.com/help/password-manager-plans/)).
- Teams costs $4 per user per month, billed annually, with no user limit, and adds event logging ([pricing](https://bitwarden.com/pricing/business/)). For two board members and a maintainer that is $144 a year.
- Bitwarden says it gives educational and nonprofit organizations a discount through a quote from its sales team. Third-party sites put it at 50%, which is not confirmed.
- Bitwarden's cloud has a US and an EU region. An organization "only exists in the region where it was first created", and moving it means creating a new one and importing an export ([server regions](https://bitwarden.com/help/server-geographies/)).

### Vaultwarden

- It describes itself as an "Unofficial Bitwarden compatible server written in Rust", licensed AGPL-3.0, and works with the official Bitwarden apps ([vaultwarden](https://github.com/dani-garcia/vaultwarden)).
- Its feature list includes organizations, collections, groups, emergency access and event logs, with no user limit.
- It is "not associated with" Bitwarden, Inc. Problems go to Vaultwarden's own channels, not Bitwarden's support.
- Being a separate implementation of the server that Bitwarden's apps talk to, it has to follow when Bitwarden changes those apps. How often an app update has broken a Vaultwarden server was not looked up.

### KeePassXC

The vault is one encrypted file on storage the association controls, opened with a free program. Nothing logs who opened what. Two people saving at the same time can conflict. Removing a person means changing the file's password and every password they could see. Not researched further.

## Agent assessment

The agent recommends Vaultwarden on the production server. It is the only free option for three people that the official Bitwarden apps work with. The upkeep is the maintainer's: the user stated that there will be one, and the vault updates the same way as the site. The user has not decided this.

### Set once in the compose file

All settings live in the compose file, so a change to them goes through a pull request like any other change. From Vaultwarden's [`.env.template`](https://github.com/dani-garcia/vaultwarden/blob/main/.env.template):

- **Its own subdomain.** `DOMAIN` is set to it: "The domain must match the address from where you access the server". A path on the site's own address would put the vault in the same origin as the Spring application, where a script injection bug in the application could reach the vault's page.
- **No public signups.** `SIGNUPS_ALLOWED=false`. The default `INVITATIONS_ALLOWED=true` "Allows org admins to invite users, even when signups are disabled".
- **No admin page.** `ADMIN_TOKEN` stays unset: "If not set, the admin panel is disabled". There is then nothing to switch on and off.
- **The web vault stays on**, the default. Inviting and removing people happens in its admin console.
- **Mail.** `SMTP_FROM` and `SMTP_HOST` "enable the mail service", which sends the invitations. The association's own mail sender does it, the same as the site's.
- **Updates.** The image is pinned to a version in the compose file. Dependabot's `docker-compose` entry in [`.github/dependabot.yml`](../../.github/dependabot.yml) already updates the pinned images there, and an automatic deploy then updates the vault the same way as the site. The 7-day cooldown in that file also delays a security fix by up to a week.
- **Backups.** Its data is in the same daily encrypted backup as the database.

### Who does what

- **A board member** accepts the invitation mail, chooses a master password, installs the Bitwarden app on their phone, chooses "self-hosted" and types the address once. The maintainer can do the last step with them at the handover. After that, they open the app when they need a password.
- **The maintainer** invites and removes people in the web vault. Before confirming a new person, the maintainer compares that person's fingerprint phrase with them by phone or in person, which defeats the key substitution described below. When a person leaves, the maintainer changes every password that person could see. Any shared vault needs that, because removing someone does not make them forget.
- **Someone who works in a service**, such as the maintainer, a student or an editor, gets their own account there instead of the shared login. Removing them is then removing that account.

A person who forgets their master password loses only their own account. The other two still reach everything shared, and the maintainer invites the person again.

### What the vault holds

- The login of every production account with its 2FA recovery codes: the host, the domain registrar, the role mailbox, GitHub, Sanity and Brevo.
- The production `.env`, as a secure note. The server keeps its own copy, readable only by its owner.
- The backup encryption key.

### The offline copy

The vault runs on the server it would help rebuild. An offline copy of the backup encryption key, and of the hosting login with its 2FA recovery code, kept in the association's binder, is what lets someone rebuild the server and restore both the register and the vault after losing it. The apps keep an encrypted copy of the vault on the device, which opens read-only without a connection as long as the person locked the app instead of logging out ([Bitwarden blog](https://bitwarden.com/blog/configuring-bitwarden-clients-for-offline-access/)). Forum reports say an app can log out on its own while the server is down, so that copy is a convenience, not the way back.

### The remaining risk: the web vault on a shared server

Bitwarden's encryption protects the vault from the server. The app stretches the master password into a key on the device, with PBKDF2-SHA256, by default at 600,000 iterations, or Argon2id ([KDF algorithms](https://bitwarden.com/help/kdf-algorithms/)), and sends the server only a hash for login. The server stores encrypted items, and someone who steals them has to guess each master password offline against that stretching.

That assumes the program doing the encryption is honest. The apps come from Bitwarden, signed, through app stores and browser extension stores, and a server cannot change them. The web vault is a web page, and the browser runs whatever HTML and JavaScript the server sends on each visit, with nothing checking it against Bitwarden's release. A changed page looks the same and works normally, and can also send the master password, or the decrypted vault, somewhere else.

On the production server, the parts that could change the page are the Spring application, which is open to the internet, Traefik, which handles HTTPS for both and could rewrite any response, the hosting provider's console, SSH, and once deploys are automatic, the deploy key that GitHub Actions holds. Whoever can run Docker on the host effectively has root on it. A dependency update that is merged without anyone reading it runs inside the application container on that server.

Taking over the server alone gives the member register and the production `.env`. A master password caught from the web vault adds every account the vault holds, including the role mailbox, which can reset the other passwords, and the hosting account, which can delete the backups.

A hostile server can attack the apps too. Researchers from ETH Zurich and USI Lugano found 12 attacks on Bitwarden by a fully malicious server, among them public keys swapped by the server, forced fallbacks to older and weaker encryption, and lowered KDF iteration counts ([USENIX Security 2026](https://www.usenix.org/conference/usenixsecurity26/presentation/scarlata), [ePrint 2026/058](https://eprint.iacr.org/2026/058)). Bitwarden says all of them are fixed and calls most of them medium or low impact ([CyberInsider](https://cyberinsider.com/popular-password-managers-fall-short-of-zero-knowledge-claims/)). Keeping the apps updating automatically, and comparing fingerprint phrases when confirming a person, cover what is left. The swap matters at confirmation, because the admin's app encrypts the organization key to whatever public key the server returns.

Two smaller reasons to use the apps: in a web vault tab, the decrypted passwords are in the page, where any browser extension with access to all sites can read them, and a master password typed into a web page can be phished with a lookalike address.

The agent suggested `WEB_VAULT_ENABLED=false` outside the few sessions that need the admin console. Vaultwarden then serves no web vault while the API keeps serving the apps ([Vaultwarden wiki](https://vaultwarden-ftf.pages.dev/Disabling-or-overriding-the-Vault-interface-hosting), [forum](https://vaultwarden.discourse.group/t/disable-web-gui-and-use-only-apps/2308)), and the setting needs the container recreated. The user rejected that as too much, see [the user's constraints](#the-users-constraints). The agent judges the risk acceptable at this size: it needs the server taken over first, and the apps are the normal way to use the vault.

## Open

- Whether the association wants a shared vault at all: C31 in [the next meeting's questions](../meetings/notes.md).
- Which two board members (C3) and which maintainer (C2), in [open-questions.md](../open-questions.md).
- The production server, the association's domain for the subdomain, and the association's mail sender for the invitations (C29 in [the next meeting's questions](../meetings/notes.md)).
- How often a Bitwarden app update has needed a newer Vaultwarden, and how fast Vaultwarden followed.

## Verification

Every quote above was read on 2026-11-11 from the page it links to. The forum reports of apps logging out are second-hand, from a search summary of the Bitwarden community forum, and were not read in full. The Bitwarden plan limits and prices come from [Password Manager plans](https://bitwarden.com/help/password-manager-plans/) and [pricing](https://bitwarden.com/pricing/business/), the Vaultwarden settings from its [`.env.template`](https://github.com/dani-garcia/vaultwarden/blob/main/.env.template) on its `main` branch, and its features from its README. Nothing was installed, and no setting above was tried.
