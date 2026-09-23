// The REST adapter: the same services as the pages, behind the same login and
// CSRF protection. docs/decisions/0014.
import { expect, test, request as http, type APIRequestContext } from "@playwright/test";
import { text } from "../support/copy";
import { administratorId, clearLinkRequests, resetAdministrators } from "../support/db";
import { clearMail, waitForMail } from "../support/mail";
import { ADMINISTRATORS, freshAddress, MEMBERS, PATHS, SITE, type Kind } from "../support/site";

test.beforeEach(async () => {
  await resetAdministrators();
  await clearLinkRequests();
});
test.afterAll(resetAdministrators);

async function csrf(client: APIRequestContext): Promise<string> {
  const response = await client.get("/api/csrf");
  expect(response.status()).toBe(200);
  return (await response.json()).token;
}

/// A client that logged in through the same URLs the pages post to, and keeps
/// the session cookie.
async function loggedIn(kind: Kind, email: string): Promise<APIRequestContext> {
  const client = await http.newContext({ baseURL: SITE });
  await clearMail(email);
  expect((await client.post(PATHS[kind].login, { form: { email, _csrf: await csrf(client) } })).ok()).toBe(true);
  const token = new URL((await waitForMail(email)).link).searchParams.get("token")!;
  const login = await client.post(PATHS[kind].link, { form: { token, _csrf: await csrf(client) }, maxRedirects: 0 });
  expect(new URL(login.headers().location, SITE).pathname).toBe(PATHS[kind].home);
  return client;
}

test("the CSRF endpoint names the header and the field", async ({ request }) => {
  expect(await (await request.get("/api/csrf")).json()).toMatchObject({
    headerName: "X-CSRF-TOKEN", parameterName: "_csrf", token: expect.any(String),
  });
});

test("an application and its confirmation over the API", async ({ request }) => {
  const email = freshAddress("api");
  await clearMail(email);
  const applied = await request.post("/api/membership-applications", {
    data: { fullName: "Api Person", email }, headers: { "X-CSRF-TOKEN": await csrf(request) },
  });
  expect(applied.status()).toBe(202);
  const token = new URL((await waitForMail(email)).link).searchParams.get("token")!;

  const confirmed = await request.post("/api/membership-applications/confirmation", {
    data: { token }, headers: { "X-CSRF-TOKEN": await csrf(request) },
  });
  expect(confirmed.status()).toBe(200);
  expect(await confirmed.json()).toMatchObject({ fullName: "Api Person", email, bankgiro: "123-4567" });

  const again = await request.post("/api/membership-applications/confirmation", {
    data: { token }, headers: { "X-CSRF-TOKEN": await csrf(request) },
  });
  expect(again.status()).toBe(404);
});

test("a member reads their own details over the API", async () => {
  const client = await loggedIn("member", MEMBERS.erik);
  const response = await client.get("/api/member");
  expect(response.status()).toBe(200);
  expect(await response.json()).toMatchObject({ fullName: "Erik Lindqvist", email: MEMBERS.erik });
  expect(await (await client.get("/api/member/passkeys")).json()).toEqual([]);
  await client.dispose();
});

test("an administrator lists, adds and removes administrators over the API", async () => {
  const client = await loggedIn("administrator", ADMINISTRATORS.ada);
  const list = await client.get("/api/admin/administrators");
  expect(list.status()).toBe(200);
  expect(await list.json()).toHaveLength(3);

  const email = freshAddress("api-admin");
  const added = await client.post("/api/admin/administrators", {
    data: { fullName: "Api Admin", email }, headers: { "X-CSRF-TOKEN": await csrf(client) },
  });
  expect(added.status()).toBe(201);
  const { id } = await added.json();
  const removed = await client.delete(`/api/admin/administrators/${id}`, {
    headers: { "X-CSRF-TOKEN": await csrf(client) },
  });
  expect(removed.status()).toBe(204);
  await client.dispose();
});

test.describe("what a client might get wrong", () => {
  test("no session gets 401, not a login page", async ({ request }) => {
    for (const path of ["/api/member", "/api/member/passkeys", "/api/admin/administrators", "/api/admin/passkeys"]) {
      const response = await request.get(path, { maxRedirects: 0 });
      expect(response.status(), path).toBe(401);
    }
  });

  test("a path no rule mentions gets a bare 404, with a session or without", async ({ request }) => {
    const client = await loggedIn("member", MEMBERS.erik);
    for (const response of [await request.get("/api/finns-inte"), await client.get("/api/finns-inte")]) {
      expect(response.status()).toBe(404);
      expect(await response.text()).toBe("");
    }
    await client.dispose();
  });

  test("a member's session gets 403 on the administrator API", async () => {
    const client = await loggedIn("member", MEMBERS.erik);
    expect((await client.get("/api/admin/administrators", { maxRedirects: 0 })).status()).toBe(403);
    await client.dispose();
  });

  test("a change without the CSRF header is refused", async ({ request }) => {
    const response = await request.post("/api/membership-applications", {
      data: { fullName: "Utan Token", email: freshAddress("utan-csrf") },
    });
    expect(response.status()).toBe(403);
  });

  test("an invalid application gets 400 with the field's message", async ({ request }) => {
    const response = await request.post("/api/membership-applications", {
      data: { fullName: "", email: "inte-en-adress" }, headers: { "X-CSRF-TOKEN": await csrf(request) },
    });
    expect(response.status()).toBe(400);
    const body = await response.json();
    expect(body.errors.fullName).toBe(text("application.fullName.required"));
    expect(body.errors.email).toBe(text("application.email.invalid"));
  });

  test("JSON that does not parse gets 400, not 500", async ({ request }) => {
    const response = await request.post("/api/membership-applications", {
      data: "{\"fullName\": ", headers: { "X-CSRF-TOKEN": await csrf(request), "Content-Type": "application/json" },
    });
    expect(response.status()).toBe(400);
  });

  test("a form post to a JSON endpoint gets 415, not 500", async ({ request }) => {
    const response = await request.post("/api/membership-applications", {
      form: { fullName: "Formulär", email: freshAddress("form") }, headers: { "X-CSRF-TOKEN": await csrf(request) },
    });
    expect(response.status()).toBe(415);
  });

  test("a blank confirmation token gets 400", async ({ request }) => {
    const response = await request.post("/api/membership-applications/confirmation", {
      data: { token: " " }, headers: { "X-CSRF-TOKEN": await csrf(request) },
    });
    expect(response.status()).toBe(400);
  });

  test("an application with no body fields at all gets 400, not 500", async ({ request }) => {
    const response = await request.post("/api/membership-applications", {
      data: {}, headers: { "X-CSRF-TOKEN": await csrf(request) },
    });
    expect(response.status()).toBe(400);
  });

  test("administrator errors come back as problem details", async () => {
    const client = await loggedIn("administrator", ADMINISTRATORS.ada);
    const headers = { "X-CSRF-TOKEN": await csrf(client) };

    const duplicate = await client.post("/api/admin/administrators", {
      data: { fullName: "Gunnar", email: ADMINISTRATORS.gunnar }, headers,
    });
    expect(duplicate.status()).toBe(409);

    expect((await client.delete("/api/admin/administrators/999999", { headers })).status()).toBe(404);
    expect((await client.delete("/api/admin/administrators/inte-ett-id", { headers })).status()).toBe(400);

    const gunnar = await administratorId(ADMINISTRATORS.gunnar);
    const karin = await administratorId(ADMINISTRATORS.karin);
    expect((await client.delete(`/api/admin/administrators/${gunnar}`, { headers })).status()).toBe(204);
    const tooFew = await client.delete(`/api/admin/administrators/${karin}`, { headers });
    expect(tooFew.status()).toBe(409);
    expect((await tooFew.json()).title).toBe("Too few administrators");

    expect((await client.delete("/api/admin/passkeys/finns-inte", { headers })).status()).toBe(404);
    await client.dispose();
  });
});
