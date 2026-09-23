// Run through e2e/run.sh, which starts the stack these tests expect:
// docs/decisions/0017.
import { defineConfig, devices } from "@playwright/test";
import { SITE } from "./support/site";

export default defineConfig({
  testDir: "tests",
  // One database for the whole run, and tests that change administrators and
  // rate limit counts in it, so one test at a time.
  workers: 1,
  fullyParallel: false,
  forbidOnly: Boolean(process.env.CI),
  reporter: [["list"], ["html", { open: "never" }]],
  use: {
    baseURL: SITE,
    locale: "sv-SE",
    // The user's own view: docs/decisions/0017.
    viewport: { width: 2560, height: 1300 },
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
  },
  projects: [
    { name: "chromium", use: { ...devices["Desktop Chrome"], viewport: { width: 2560, height: 1300 } } },
    {
      name: "firefox",
      use: { ...devices["Desktop Firefox"], viewport: { width: 2560, height: 1300 } },
      // The software authenticator is Chromium's DevTools protocol only.
      testIgnore: /passkeys\.spec\.ts/,
    },
  ],
});
