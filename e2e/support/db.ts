// Direct access to the e2e stack's database, for what a browser cannot do in
// a test's time: make a link older than its lifetime, or put the
// administrators back as the seed left them. e2e/run.sh sets the password.
import pg from "pg";
import { ADMINISTRATORS } from "./site";

const pool = new pg.Pool({
  host: "localhost",
  port: 55433,
  database: "teaterihuskvarna",
  user: "teaterihuskvarna",
  password: "e2e",
  max: 2,
  // Every spec file shares this pool in one worker, so none may end it; the
  // worker exits once the pool is idle.
  allowExitOnIdle: true,
});

export async function sql<T extends pg.QueryResultRow = pg.QueryResultRow>(
  text: string, values: unknown[] = []): Promise<T[]> {
  return (await pool.query<T>(text, values)).rows;
}

/// Every login link sent to `email` expires now.
export async function expireLoginLinks(email: string): Promise<void> {
  await sql("UPDATE one_time_token SET expires_at = now() - interval '1 minute' WHERE email = lower($1)", [email]);
}

/// The pending application for `email` expires now.
export async function expireApplication(email: string): Promise<void> {
  await sql("UPDATE membership_application SET expires_at = now() - interval '1 minute' WHERE lower(email) = lower($1)",
    [email]);
}

/// Forgets every request the rate limit counted.
export async function clearLinkRequests(): Promise<void> {
  await sql("DELETE FROM link_request");
}

/// Removes every passkey the server knows about.
export async function clearPasskeys(): Promise<void> {
  await sql("DELETE FROM user_credentials");
  await sql("DELETE FROM user_entities");
}

/// Exactly the three seed administrators active, and anyone a test added
/// removed. Rows stay, as the application keeps them.
export async function resetAdministrators(): Promise<void> {
  const seed = Object.values(ADMINISTRATORS);
  await sql("UPDATE administrator SET removed_at = NULL, removed_by = NULL WHERE lower(email) = ANY($1)", [seed]);
  await sql("UPDATE administrator SET removed_at = now() WHERE removed_at IS NULL AND NOT lower(email) = ANY($1)",
    [seed]);
}

export async function administratorId(email: string): Promise<number> {
  const rows = await sql<{ id: string }>("SELECT id FROM administrator WHERE lower(email) = lower($1)", [email]);
  return Number(rows[0].id);
}

export async function activeAdministrators(): Promise<string[]> {
  const rows = await sql<{ email: string }>(
    "SELECT email FROM administrator WHERE removed_at IS NULL ORDER BY email");
  return rows.map((row) => row.email);
}
