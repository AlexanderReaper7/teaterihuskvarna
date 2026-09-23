// A software authenticator in Chromium, through the DevTools protocol's
// WebAuthn domain. It answers every ceremony at once, conditional mediation
// (the address field's autofill) included, as a platform authenticator with
// the person's fingerprint already accepted would.
import type { BrowserContext, CDPSession, Page } from "@playwright/test";

export type Authenticator = {
  cdp: CDPSession;
  id: string;
  credentials(): Promise<{ credentialId: string; userName?: string; isResidentCredential: boolean }[]>;
  /// false makes the next ceremony fail as a cancelled dialog does.
  setUserVerified(verified: boolean): Promise<void>;
};

export async function addAuthenticator(page: Page): Promise<Authenticator> {
  const cdp = await page.context().newCDPSession(page);
  await cdp.send("WebAuthn.enable");
  const { authenticatorId } = await cdp.send("WebAuthn.addVirtualAuthenticator", {
    options: {
      protocol: "ctap2",
      transport: "internal",
      hasResidentKey: true,
      hasUserVerification: true,
      isUserVerified: true,
      automaticPresenceSimulation: true,
    },
  });
  return {
    cdp,
    id: authenticatorId,
    credentials: async () => (await cdp.send("WebAuthn.getCredentials", { authenticatorId })).credentials,
    setUserVerified: async (isUserVerified) => {
      await cdp.send("WebAuthn.setUserVerified", { authenticatorId, isUserVerified });
    },
  };
}

/// Makes every page in the context report no autofill for passkeys, so a
/// login page waits for the button rather than logging in on load.
export async function withoutAutofill(context: BrowserContext): Promise<void> {
  await context.addInitScript(() => {
    if (window.PublicKeyCredential) {
      PublicKeyCredential.isConditionalMediationAvailable = async () => false;
    }
  });
}
