// The countdown before a login ends, on /medlem and /admin. A login lasts a
// fixed time from when it started, whatever happens meanwhile:
// docs/decisions/0015, "A login ends a fixed time after it starts". Two
// minutes before the end the page beeps once, counts down and offers to
// extend the login. At the end, or once the server says the login is gone,
// the page stays as it is and says so, with a link to the plain login page.
// Every Swedish text comes from the page.
//
// The page gives the seconds left, not the time of the end, because this
// computer's clock may not agree with the server's. Before it warns, and at
// the end, it asks the server again, since another tab may have extended the
// login or another device may have ended it. It also asks when the tab comes
// back into view, at most once a minute, so a page left open on a computer
// that was logged out from elsewhere says so when someone looks at it.
// Each question counts as a use of the login, and moves "Senast använd".
"use strict";

(() => {
  const root = document.querySelector("[data-login-countdown]");
  if (!root) {
    return;
  }
  const WARN_MILLIS = 120_000;
  const RECHECK_MILLIS = 60_000;
  const RETRY_MILLIS = 10_000;

  const ending = root.querySelector("[data-login-ending]");
  const ended = root.querySelector("[data-login-ended]");
  const remaining = root.querySelector("[data-login-remaining]");

  let deadline = Date.now() + Number(root.dataset.secondsLeft) * 1000;
  let over = false;
  let asking = false;
  let lastAsked = Date.now();
  let retryAt = 0;

  async function ask(url, method) {
    const response = await fetch(url, {
      method,
      headers: {
        Accept: "application/json",
        // The masked token from any form on the page, as passkey.js sends it.
        "X-CSRF-TOKEN": document.querySelector("input[name=_csrf]")?.value ?? "",
      },
    });
    if (response.status === 401) {
      return null;
    }
    if (!response.ok) {
      throw new Error(`${url} answered HTTP ${response.status}`);
    }
    return response.json();
  }

  function loggedOut() {
    over = true;
    ending.hidden = true;
    ended.hidden = false;
    // Set when shown, so a screen reader announces it.
    ended.querySelector("[data-login-ended-text]").textContent = root.dataset.ended;
  }

  // Asks the server, and moves the deadline to its answer. Resolves to
  // "answered", "gone" once the login is gone, or "failed".
  async function refresh(url, method) {
    asking = true;
    try {
      const status = await ask(url, method);
      lastAsked = Date.now();
      if (status === null) {
        loggedOut();
        return "gone";
      }
      deadline = Date.now() + status.secondsLeft * 1000;
      return "answered";
    } catch (error) {
      // Offline, or the server is restarting. The clock keeps counting, and
      // the next question waits a while rather than coming every second.
      console.error(error);
      retryAt = Date.now() + RETRY_MILLIS;
      return "failed";
    } finally {
      asking = false;
    }
  }

  // A short, quiet tone. Browsers play sound only on a page the person has
  // clicked or typed on; elsewhere this stays silent, and the text still shows.
  function beep() {
    try {
      const context = new AudioContext();
      const tone = context.createOscillator();
      const volume = context.createGain();
      tone.frequency.value = 880;
      volume.gain.setValueAtTime(0.1, context.currentTime);
      volume.gain.exponentialRampToValueAtTime(0.0001, context.currentTime + 0.4);
      tone.connect(volume).connect(context.destination);
      tone.start();
      tone.stop(context.currentTime + 0.4);
      setTimeout(() => context.close(), 1000);
    } catch (error) {
      console.error(error);
    }
  }

  function minutesAndSeconds(millis) {
    const seconds = Math.max(0, Math.ceil(millis / 1000));
    return `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, "0")}`;
  }

  async function tick() {
    if (over || asking) {
      return;
    }
    const mayAsk = Date.now() >= retryAt;
    if (mayAsk && deadline - Date.now() <= 0) {
      await refresh(root.dataset.status, "GET");
    } else if (mayAsk && deadline - Date.now() <= WARN_MILLIS && ending.hidden) {
      if (await refresh(root.dataset.status, "GET") === "gone" || deadline - Date.now() > WARN_MILLIS) {
        return;
      }
      ending.hidden = false;
      ending.querySelector("[data-login-ending-text]").textContent = root.dataset.ending;
      beep();
    }
    if (over) {
      return;
    }
    if (deadline - Date.now() > WARN_MILLIS) {
      ending.hidden = true;
    } else {
      remaining.textContent = root.dataset.remaining.replace("{0}", minutesAndSeconds(deadline - Date.now()));
    }
  }

  root.querySelector("[data-login-extend]").addEventListener("click", async () => {
    if (await refresh(root.dataset.extend, "POST") === "answered") {
      ending.hidden = true;
    }
  });

  document.addEventListener("visibilitychange", () => {
    if (document.visibilityState === "visible" && !over && !asking
        && Date.now() - lastAsked > RECHECK_MILLIS) {
      refresh(root.dataset.status, "GET");
    }
  });

  setInterval(tick, 1000);
})();
