// Mailpit's API: https://mailpit.axllent.org/docs/api-v1/
import { MAILPIT } from "./site";

type Summary = { ID: string; Subject: string; Created: string };

async function search(to: string): Promise<Summary[]> {
  const query = encodeURIComponent(`to:"${to}"`);
  const response = await fetch(`${MAILPIT}/api/v1/search?query=${query}`);
  if (!response.ok) {
    throw new Error(`Mailpit search answered ${response.status}`);
  }
  return (await response.json()).messages ?? [];
}

/// Deletes every mail to `to`, so the next one found is the one the test caused.
export async function clearMail(to: string): Promise<void> {
  const query = encodeURIComponent(`to:"${to}"`);
  await fetch(`${MAILPIT}/api/v1/search?query=${query}`, { method: "DELETE" });
}

/// `code` is the six digit login code on a line of its own, where the mail has one.
export type Mail = { subject: string; text: string; link: string; code?: string };

/// Waits for a mail to `to` and returns it with the first site link in it.
/// The application sends mail off the request thread, so it arrives a moment
/// after the page answers.
export async function waitForMail(to: string, timeoutMs = 10_000): Promise<Mail> {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    const found = await search(to);
    if (found.length > 0) {
      const message = await (await fetch(`${MAILPIT}/api/v1/message/${found[0].ID}`)).json();
      const link = /http:\/\/localhost:55556\/\S+/.exec(message.Text)?.[0];
      if (!link) {
        throw new Error(`Mail to ${to} has no site link:\n${message.Text}`);
      }
      const code = /^(\d{6})$/m.exec(message.Text)?.[1];
      return { subject: message.Subject, text: message.Text, link, code };
    }
    await new Promise((resolve) => setTimeout(resolve, 200));
  }
  throw new Error(`No mail to ${to} within ${timeoutMs} ms`);
}

/// How many mails `to` has, after waiting long enough that one sent by the
/// last request would have arrived.
export async function mailCountAfterWait(to: string, waitMs = 3_000): Promise<number> {
  await new Promise((resolve) => setTimeout(resolve, waitMs));
  return (await search(to)).length;
}
