// Writes the licence of every package esbuild put into the bundle, read from
// its metafile, to dist/visual-editing.licenses.txt, which the image serves
// beside the bundle. The minified file keeps only comments marked @license,
// and most of the bundled packages have none.
import {readFileSync, readdirSync, writeFileSync} from 'node:fs'
import {join} from 'node:path'

const meta = JSON.parse(readFileSync('dist/meta.json', 'utf8'))
const packages = new Set()
for (const output of Object.values(meta.outputs)) {
  for (const input of Object.keys(output.inputs)) {
    // The innermost node_modules entry, so a nested copy counts as its own package.
    const match = input.match(/^(.*node_modules\/(?:@[^/]+\/)?[^/]+)/)
    if (match) packages.add(match[1])
  }
}

const sections = [...packages].sort().map(dir => {
  const pkg = JSON.parse(readFileSync(join(dir, 'package.json'), 'utf8'))
  const file = readdirSync(dir).find(name => /^(licen[cs]e|copying)/i.test(name))
  const text = file ? readFileSync(join(dir, file), 'utf8').trim() : 'The package has no licence file.'
  return `${pkg.name} ${pkg.version}, ${pkg.license}\n\n${text}`
})
writeFileSync('dist/visual-editing.licenses.txt', sections.join(`\n\n${'-'.repeat(72)}\n\n`) + '\n')
