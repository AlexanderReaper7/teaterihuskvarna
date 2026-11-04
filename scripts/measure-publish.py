"""Times a Sanity publish until the public page shows it, for R008.

    python3 scripts/measure-publish.py api --runs 5 [--prime 0.5]
    python3 scripts/measure-publish.py watch

Reads SANITY_PROJECT_ID, SANITY_DATASET_NAME and SANITY_API_KEY from .env,
or from the environment, which wins.
The key needs write access to the dataset. The script creates a test news
item dated 2000-01-01, so it sits last in the news list, and deletes it at
the end unless --keep is given. Each run sets the item's ingress to a fresh
marker and polls the item's page every 0.1 s until the marker appears.

api:   publishes through the API. The time counts from Sanity accepting the
       publish. With --prime, the page is fetched first and the publish waits
       that many seconds, so the cache was refreshed just before it: the
       slowest case when the webhook does not arrive.
watch: waits for an editor to change the ingress and publish in Studio. The
       time counts from Sanity's listener reporting the publish.

--base-url is the site, http://localhost:<PROXY_HTTP_PORT> by default. A
shared stack behind its password takes --header 'Authorization: Basic ...'.
One JSON line per run goes to stdout. Results: docs/decisions/0021, Verification.
"""

import argparse
import json
import os
import time
import urllib.parse
import urllib.request
from pathlib import Path

DOC = "measure-publish-test"
SLUG = "measure-publish-test"
API_VERSION = "v2025-01-01"


def dotenv():
    values = {}
    for line in (Path(__file__).resolve().parent.parent / ".env").read_text(encoding="utf-8").splitlines():
        if "=" in line and not line.lstrip().startswith("#"):
            key, value = line.split("=", 1)
            values[key.strip()] = value.strip().strip("'\"")
    values.update({k: v for k, v in os.environ.items() if k.startswith(("SANITY_", "PROXY_"))})
    return values


class Sanity:
    def __init__(self, project, dataset, token):
        self.base = f"https://{project}.api.sanity.io/{API_VERSION}"
        self.dataset = dataset
        self.token = token

    def request(self, path, body=None):
        request = urllib.request.Request(
            f"{self.base}{path}", method="POST" if body is not None else "GET",
            data=None if body is None else json.dumps(body).encode(),
            headers={"Authorization": f"Bearer {self.token}", "Content-Type": "application/json"})
        return urllib.request.urlopen(request, timeout=30)

    def mutate(self, *mutations):
        with self.request(f"/data/mutate/{self.dataset}?visibility=sync", {"mutations": list(mutations)}) as answer:
            return json.load(answer)

    def next_publish(self):
        """Blocks until the published test item changes, and returns its ingress."""
        query = urllib.parse.urlencode({"query": f'*[_id == "{DOC}"]', "includeResult": "true"})
        with self.request(f"/data/listen/{self.dataset}?{query}") as stream:
            for raw in stream:
                line = raw.decode().strip()
                if line.startswith("data: "):
                    data = json.loads(line[6:])
                    ingress = (data.get("result") or {}).get("ingress")
                    if data.get("documentId") == DOC and ingress:
                        return ingress
        raise RuntimeError("the listener closed")


def fetch(url, headers):
    request = urllib.request.Request(url, headers=headers)
    try:
        with urllib.request.urlopen(request, timeout=10) as answer:
            return answer.read().decode()
    except urllib.error.HTTPError as error:
        return f"HTTP {error.code}"


def wait_for(url, headers, marker, timeout=120):
    start = time.monotonic()
    while time.monotonic() - start < timeout:
        if marker in fetch(url, headers):
            return time.time()
        time.sleep(0.1)
    return None


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("mode", choices=["api", "watch"])
    parser.add_argument("--runs", type=int, default=1)
    parser.add_argument("--prime", type=float)
    parser.add_argument("--base-url")
    parser.add_argument("--header", action="append", default=[])
    parser.add_argument("--keep", action="store_true")
    args = parser.parse_args()

    env = dotenv()
    sanity = Sanity(env["SANITY_PROJECT_ID"], env.get("SANITY_DATASET_NAME") or "dev", env["SANITY_API_KEY"])
    base = (args.base_url or f"http://localhost:{env.get('PROXY_HTTP_PORT') or 8000}").rstrip("/")
    page = f"{base}/nyheter/{SLUG}"
    headers = dict(h.split(":", 1) for h in args.header)
    headers = {k.strip(): v.strip() for k, v in headers.items()}

    sanity.mutate({"createIfNotExists": {
        "_id": DOC, "_type": "nyhet", "title": "Testnyhet för mätning av publicering",
        "slug": {"_type": "slug", "current": SLUG}, "publiceringsdatum": "2000-01-01T10:00:00Z",
        "ingress": "Startvärde. Tas bort efter mätningen."}})
    try:
        for run in range(1, args.runs + 1):
            if args.mode == "api":
                primed = None
                if args.prime is not None:
                    fetch(page, headers)
                    primed = time.time()
                    time.sleep(args.prime)
                marker = f"measure-{run}-{int(time.time())}"
                sent = time.time()
                sanity.mutate({"patch": {"id": DOC, "set": {"ingress": marker}}})
                accepted = time.time()
                seen = wait_for(page, headers, marker)
                print(json.dumps({
                    "run": run, "mode": "api",
                    "accept_s": round(accepted - sent, 2),
                    "refreshed_before_s": None if primed is None else round(accepted - primed, 2),
                    "visible_after_s": None if seen is None else round(seen - accepted, 2),
                }), flush=True)
            else:
                print(json.dumps({"run": run, "waiting": f"change the ingress of {DOC} in Studio and publish"}), flush=True)
                marker = sanity.next_publish()
                published = time.time()
                seen = wait_for(page, headers, marker)
                print(json.dumps({
                    "run": run, "mode": "watch",
                    "visible_after_s": None if seen is None else round(seen - published, 2),
                }), flush=True)
    finally:
        if not args.keep:
            sanity.mutate({"delete": {"id": DOC}}, {"delete": {"id": f"drafts.{DOC}"}})


if __name__ == "__main__":
    main()
