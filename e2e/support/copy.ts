// The site's Swedish copy, read from the same file the application reads, so a
// reworded message changes the test's expectation with it.
import { readFileSync } from "node:fs";
import { resolve } from "node:path";

const file = resolve(__dirname, "../../src/main/resources/messages_sv.properties");

// Enough of the properties format for this one file: `key=value`, `#`
// comments, a trailing backslash continuing the value on the next line with
// its leading spaces dropped, and `\n` for a line break.
function parse(source: string): Map<string, string> {
  const messages = new Map<string, string>();
  const lines = source.split("\n");
  for (let i = 0; i < lines.length; i++) {
    let line = lines[i].trimStart();
    if (line === "" || line.startsWith("#")) {
      continue;
    }
    while (line.endsWith("\\") && i + 1 < lines.length) {
      line = line.slice(0, -1) + lines[++i].trimStart();
    }
    const equals = line.indexOf("=");
    messages.set(line.slice(0, equals), line.slice(equals + 1).replace(/\\n/g, "\n"));
  }
  return messages;
}

const messages = parse(readFileSync(file, "utf8"));

/// The copy for `key`, with `{0}`, `{1}` and so on replaced by `args`.
export function text(key: string, ...args: string[]): string {
  const message = messages.get(key);
  if (message === undefined) {
    throw new Error(`No key ${key} in messages_sv.properties`);
  }
  return args.reduce((result, arg, index) => result.replaceAll(`{${index}}`, arg), message);
}
