// Passkeys in the browser: the button and the address field's autofill on the
// login pages, and adding a passkey on /medlem and /admin. The server side is
// Spring Security's passkey filters, on the paths each page names in its data
// attributes: docs/decisions/0016. The request and response bodies are the
// ones Spring's own script sends, spring-webauthn.js in
// spring-security-webauthn 7.1.1. Every Swedish text comes from the page.
//
// Without WebAuthn in the browser, or without JavaScript, the passkey buttons
// stay hidden and login links work as before.
"use strict";

(() => {
  const supported = Boolean(window.PublicKeyCredential);

  // WebAuthn hands out bytes; Spring reads and writes them as base64url.
  const bytes = (base64url) =>
    Uint8Array.from(atob(base64url.replace(/-/g, "+").replace(/_/g, "/")), (c) => c.charCodeAt(0));
  const base64url = (buffer) =>
    btoa(String.fromCharCode(...new Uint8Array(buffer)))
      .replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");

  async function post(url, body) {
    const response = await fetch(url, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        // The masked token from any form on the page; Spring accepts it in
        // the header as well as in a field.
        "X-CSRF-TOKEN": document.querySelector("input[name=_csrf]")?.value ?? "",
      },
      body: body === undefined ? undefined : JSON.stringify(body),
    });
    if (!response.ok) {
      throw new Error(`${url} answered HTTP ${response.status}`);
    }
    return response.json();
  }

  function show(element, text) {
    if (!element) {
      return;
    }
    if (text !== undefined) {
      element.textContent = text;
    }
    element.hidden = false;
  }

  // The person closed the browser's dialog, or another ceremony replaced this
  // one. Neither is worth an error message.
  const cancelled = (error) => error?.name === "NotAllowedError" || error?.name === "AbortError";

  function setUpLogin(section) {
    const error = section.querySelector("[data-passkey-error]");
    let ceremony = new AbortController();

    // Asks the browser for a passkey, and resolves once the person has picked one.
    async function choose(mediation) {
      ceremony.abort();
      ceremony = new AbortController();
      const options = await post(section.dataset.options);
      return navigator.credentials.get({
        mediation,
        signal: ceremony.signal,
        publicKey: {
          ...options,
          challenge: bytes(options.challenge),
          allowCredentials: (options.allowCredentials ?? []).map((c) => ({ ...c, id: bytes(c.id) })),
        },
      });
    }

    async function logIn(credential) {
      const response = credential.response;
      const result = await post(section.dataset.login, {
        id: credential.id,
        rawId: base64url(credential.rawId),
        response: {
          authenticatorData: base64url(response.authenticatorData),
          clientDataJSON: base64url(response.clientDataJSON),
          signature: base64url(response.signature),
          userHandle: response.userHandle ? base64url(response.userHandle) : undefined,
        },
        type: credential.type,
        clientExtensionResults: credential.getClientExtensionResults(),
        authenticatorAttachment: credential.authenticatorAttachment,
      });
      window.location.assign(result.redirectUrl);
    }

    function failed(reason) {
      if (!cancelled(reason)) {
        console.error(reason);
        show(error, section.dataset.error);
      }
    }

    function quiet(reason) {
      if (!cancelled(reason)) {
        console.error(reason);
      }
    }

    // Autofill: the browser lists passkeys among the address field's
    // suggestions, and picking one logs in. It waits until then, and a click
    // on the button replaces it. Started again only after the button's
    // attempt ends, never after its own, which could otherwise loop.
    //
    // Until a passkey is picked, nobody has asked for anything, so a failure
    // only goes to the console. Headless Chromium, for one, says autofill is
    // available and then refuses the request. Once a passkey is picked, a
    // failure shows the message, as it does for the button.
    async function offerInAutofill() {
      if (await PublicKeyCredential.isConditionalMediationAvailable?.()) {
        choose("conditional").then(logIn, quiet).catch(failed);
      }
    }

    section.querySelector("[data-passkey-login-button]").addEventListener("click", () => {
      error.hidden = true;
      choose("optional").then(logIn).catch((reason) => {
        failed(reason);
        offerInAutofill();
      });
    });
    section.hidden = false;
    offerInAutofill();
  }

  // What the list on /medlem and /admin calls the passkey, such as "Firefox
  // på Windows". Read from the browser rather than asked for, so adding one
  // is a single press.
  function label(pattern, unknown) {
    const agent = navigator.userAgent;
    const browser = [
      [/Edg\//, "Edge"], [/OPR\//, "Opera"], [/Firefox\/|FxiOS/, "Firefox"],
      [/Chrome\/|CriOS/, "Chrome"], [/Safari\//, "Safari"],
    ].find(([test]) => test.test(agent))?.[1];
    const system = [
      [/Windows/, "Windows"], [/Android/, "Android"], [/iPhone/, "iOS"], [/iPad/, "iPadOS"],
      [/CrOS/, "ChromeOS"], [/Mac OS X/, "macOS"], [/Linux/, "Linux"],
    ].find(([test]) => test.test(agent))?.[1];
    return pattern.replace("{0}", browser ?? unknown).replace("{1}", system ?? unknown);
  }

  function setUpRegistration(section) {
    const offer = document.querySelector("[data-passkey-offer]");

    async function add() {
      const options = await post(section.dataset.options);
      const credential = await navigator.credentials.create({
        publicKey: {
          ...options,
          user: { ...options.user, id: bytes(options.user.id) },
          challenge: bytes(options.challenge),
          excludeCredentials: (options.excludeCredentials ?? []).map((c) => ({ ...c, id: bytes(c.id) })),
        },
      });
      const response = credential.response;
      const result = await post(section.dataset.register, {
        publicKey: {
          label: label(section.dataset.label, section.dataset.unknown),
          credential: {
            id: credential.id,
            rawId: base64url(credential.rawId),
            response: {
              attestationObject: base64url(response.attestationObject),
              clientDataJSON: base64url(response.clientDataJSON),
              transports: response.getTransports?.() ?? [],
            },
            type: credential.type,
            clientExtensionResults: credential.getClientExtensionResults(),
            authenticatorAttachment: credential.authenticatorAttachment,
          },
        },
      });
      if (!result.success) {
        throw new Error(`${section.dataset.register} answered ${JSON.stringify(result)}`);
      }
      // The list is the confirmation.
      window.location.assign(window.location.pathname);
    }

    for (const button of document.querySelectorAll("[data-passkey-add]")) {
      const error = button.closest("[data-passkey-offer], [data-passkeys]").querySelector("[data-passkey-error]");
      button.addEventListener("click", () => {
        error.hidden = true;
        add().catch((reason) => {
          if (reason?.name === "InvalidStateError") {
            show(error, section.dataset.exists);
          } else if (cancelled(reason)) {
            show(error, section.dataset.cancelled);
          } else {
            console.error(reason);
            show(error, section.dataset.error);
          }
        });
      });
      button.hidden = false;
    }
    show(offer);
    offer?.querySelector("[data-passkey-dismiss]").addEventListener("click", () => {
      offer.hidden = true;
    });
  }

  const login = document.querySelector("[data-passkey-login]");
  const registration = document.querySelector("[data-passkeys]");
  if (!supported) {
    show(registration?.querySelector("[data-passkey-unsupported]"));
    return;
  }
  if (login) {
    setUpLogin(login);
  }
  if (registration) {
    setUpRegistration(registration);
  }
})();
