// Where the e2e stack answers, and the seed data DevSeed puts in its empty
// database. e2e/compose.e2e.yaml publishes the ports.

export const SITE = "http://localhost:55556";
// compose.dev.yaml serves Mailpit under /mailpit/ (MP_WEBROOT).
export const MAILPIT = "http://localhost:55025/mailpit";

export type Kind = "member" | "administrator";

export const PATHS = {
  member: {
    login: "/logga-in",
    sent: "/logga-in/skickat",
    link: "/logga-in/lank",
    home: "/medlem",
    logout: "/logga-ut",
    passkeys: "/medlem/passkeys",
  },
  administrator: {
    login: "/admin/logga-in",
    sent: "/admin/logga-in/skickat",
    link: "/admin/logga-in/lank",
    home: "/admin",
    logout: "/admin/logga-ut",
    passkeys: "/admin/passkeys",
  },
} as const;

/// Members with an account, from DevSeed.
export const MEMBERS = {
  erik: "erik.lindqvist@example.test",
  maria: "maria.lindqvist@example.test",
  johan: "johan.bergstrom@example.test",
  sara: "sara.bergstrom@example.test",
  karin: "karin.holmberg@example.test",
  anders: "anders.sjoberg@example.test",
} as const;

/// The three administrators DevSeed and application-dev.yaml create. Karin is
/// a member as well.
export const ADMINISTRATORS = {
  ada: "admin@example.test",
  karin: "karin.holmberg@example.test",
  gunnar: "gunnar.wik@example.test",
} as const;

/// An address nobody has used, so tests that apply or add an administrator do
/// not depend on each other or on an earlier run against the same database.
export function freshAddress(label: string): string {
  return `${label}-${Date.now()}-${Math.floor(Math.random() * 1e6)}@example.test`;
}
