// Turns the snapshot of the old WordPress site into an NDJSON file that
// `sanity dataset import` reads. Run it from studio/, through the Studio's own
// bundler, so the schema it converts against is the one editors use:
//
//   npx sanity exec import/wordpress.ts -- <snapshot> <approved.json> <out.ndjson> [--keep-private-contacts]
//
// <snapshot> holds export/wp-dump.json, export/bricks-pages.json and
// export/media/, read out of the old site on 2026-10-24. <approved.json> holds
// the alt texts the user approved. README.md says how to load the file.
//
// The same input always gives the same file: ids come from WordPress
// (`wp-<id>`, or the existing page's id), block keys from a counter, and the
// documents are sorted by id. A rerun of the import therefore replaces rather
// than duplicates.
import {readdirSync, readFileSync, writeFileSync} from 'node:fs'
import {join, resolve} from 'node:path'
import {htmlToBlocks} from '@portabletext/block-tools'
import {createSchema, type ArraySchemaType} from 'sanity'
import {JSDOM} from 'jsdom'
import {schemaTypes} from '../schemaTypes'
import {swedishSlug} from '../slug'

type Json = Record<string, unknown>
type Field = {label: string | null; type: string; value: unknown}
type WpRecord = {rest: Json; form: {fields: Field[]}}
type Approved = {wp_id: number; file: string; alt: string}
type BricksElement = {id: string; name: string; parent: string | number; children: string[]; settings: unknown}
type Doc = Json & {_id: string; _type: string}

const args = process.argv.slice(2).filter((arg) => !arg.startsWith('--'))
if (args.length !== 3) {
  console.error('Usage: npx sanity exec import/wordpress.ts -- <snapshot> <approved.json> <out.ndjson>')
  process.exit(2)
}
const [snapshot, approvedFile, outFile] = args.map((arg) => resolve(arg))
const keepPrivateContacts = process.argv.includes('--keep-private-contacts')

const dump = JSON.parse(readFileSync(join(snapshot, 'export/wp-dump.json'), 'utf8')) as {
  types: Record<string, {records: WpRecord[]}>
  taxonomies: Record<string, {items: {id: number; name: string}[]}>
}
const bricks = JSON.parse(readFileSync(join(snapshot, 'export/bricks-pages.json'), 'utf8')) as Record<
  string,
  {content?: BricksElement[]}
>
const approved = new Map(
  (JSON.parse(readFileSync(approvedFile, 'utf8')) as {images: Approved[]}).images.map((image) => [image.wp_id, image]),
)
const mediaDir = join(snapshot, 'export/media')
const mediaFiles = readdirSync(mediaDir)

const blockContent = createSchema({name: 'import', types: schemaTypes}).get('blockContent') as ArraySchemaType
const report: string[] = []
let heldBack = 0

// Personal mail providers. An address at one of them belongs to a person,
// and both datasets answer anonymous queries, so whatever is imported is
// public. The association's own addresses stay. The snapshot already says
// "[redacted]" where WordPress had a contact field.
const PRIVATE_MAIL = /[\w.+-]+@(?:gmail|hotmail|outlook|live|yahoo|icloud|me|msn|telia)\.[a-z.]+/gi
const MOBILE = /(?:\+46|0)\s?7[02369](?:[\s-]?\d){7}/g
const REDACTED = /\[redacted\]/g
const HELD_BACK = '(kontaktuppgift borttagen)'

/** Text without tags and entities. */
function plain(html: string): string {
  return (JSDOM.fragment(`<div>${html}</div>`).textContent ?? '').replace(/\s+/g, ' ').trim()
}

/** The text of a converted block, or '' for an image. */
function blockText(block: Json | undefined): string {
  const children = (block?.children ?? []) as {text?: string}[]
  return children
    .map((child) => child.text ?? '')
    .join('')
    .replace(/\s+/g, ' ')
    .trim()
}

/** A form field's value by its label, ignoring empty ones. */
function field(record: WpRecord, label: string | null, type?: string): string | undefined {
  const found = record.form.fields.find(
    (f) => f.label === label && (type === undefined || f.type === type) && f.value !== '' && f.value !== null,
  )
  return found === undefined ? undefined : String(found.value)
}

/**
 * WordPress keeps a text field as written, with blank lines between
 * paragraphs, and adds the <p> tags only when it shows the page. This adds
 * them the same way.
 */
function paragraphs(raw: string): string {
  const block = /^\s*<(p|div|ul|ol|h[1-6]|blockquote|table|figure)[\s>]/i
  return raw
    .replace(/\r\n/g, '\n')
    .split(/\n\s*\n/)
    .map((part) => part.trim())
    .filter((part) => part !== '' && part !== '&nbsp;')
    .map((part) => (block.test(part) ? part : `<p>${part.replace(/\n/g, '<br>')}</p>`))
    .join('\n')
}

/**
 * Links to the old site point at the new site's pages, and private contacts
 * are held back. A link to a file the old site hosted keeps its text and
 * loses the link: the files are annual meeting papers, which belong among
 * the member documents (R015) and are uploaded there by hand.
 */
function clean(html: string, where: string): string {
  let out = html
    .replace(/https?:\/\/(?:www\.)?teaterihuskvarna\.se\/news\/([\w-]+)\/?/g, '/nyheter/$1')
    .replace(/https?:\/\/(?:www\.)?teaterihuskvarna\.se\/news\/?/g, '/nyheter')
    .replace(/https?:\/\/(?:www\.)?teaterihuskvarna\.se\/produktion\/[\w-]+\/?/g, '/produktioner')
    .replace(/https?:\/\/(?:www\.)?teaterihuskvarna\.se\/(om-foreningen|styrelsen|kontakt|partners)\/?/g, '/$1')
    .replace(
      /<a[^>]+href="https?:\/\/(?:www\.)?teaterihuskvarna\.se\/wp-content\/uploads\/[^"]*\/([^"/]+)"[^>]*>(.*?)<\/a>/gi,
      (_anchor, file: string, text: string) => {
        report.push(`${where}: link to ${file} removed, upload it to the member documents`)
        return text
      },
    )
  if (!keepPrivateContacts) {
    out = out.replace(/<a[^>]+href="mailto:([^"]+)"[^>]*>.*?<\/a>/gi, (anchor, address: string) =>
      address.match(PRIVATE_MAIL) ? hold() : anchor,
    )
    out = out.replace(PRIVATE_MAIL, hold).replace(MOBILE, hold)
  }
  // "meddela <address> <number>" reads as one gap, not two.
  return out.replace(REDACTED, hold).replaceAll(`${HELD_BACK} ${HELD_BACK}`, HELD_BACK)
}

function hold(): string {
  heldBack++
  return HELD_BACK
}

/** Rich text for a field of type blockContent, with keys `<prefix>-<n>`. */
function blocks(html: string, prefix: string): Json[] {
  let n = 0
  const converted = htmlToBlocks(clean(html, prefix), blockContent, {
    parseHtml: (source) => new JSDOM(source).window.document,
    keyGenerator: () => `${prefix}-${n++}`,
  }) as Json[]
  return converted.filter((block) => block._type !== 'block' || blockText(block) !== '')
}

/** An image with its approved alt text, or undefined when there is none. */
function image(wpId: unknown, where: string, key?: string): Json | undefined {
  const id = Number(wpId)
  if (!id) {
    return undefined
  }
  const text = approved.get(id)
  const file = mediaFiles.find((name) => name.startsWith(`${id}-`))
  if (text === undefined || file === undefined) {
    report.push(`image ${id} on ${where}: left out, ${text === undefined ? 'no approved alt text' : 'no file'}`)
    return undefined
  }
  return {
    ...(key === undefined ? {} : {_key: key}),
    _type: 'image',
    alt: text.alt,
    _sanityAsset: `image@file://${join(mediaDir, file)}`,
  }
}

function id(record: WpRecord): string {
  const published = record.rest.status === 'publish'
  if (!published) {
    report.push(`${record.rest.type} ${record.rest.id}: ${record.rest.status} in WordPress, so a draft`)
  }
  return `${published ? '' : 'drafts.'}wp-${record.rest.id}`
}

function title(record: WpRecord): string {
  const rendered = (record.rest.title as {rendered?: string} | undefined)?.rendered ?? ''
  return plain(rendered)
}

function slug(record: WpRecord): Json {
  const current = String(record.rest.slug)
  return {_type: 'slug', current: /^\d+$/.test(current) ? swedishSlug(title(record)) : current}
}

function withoutEmpty(doc: Doc): Doc {
  return Object.fromEntries(Object.entries(doc).filter(([, value]) => value !== undefined)) as Doc
}

const docs: Doc[] = []

for (const record of dump.types.news.records) {
  const key = `wp-${record.rest.id}`
  // The gallery is one field per image, each holding the image's id.
  const gallery = record.form.fields
    .filter((f) => f.label === 'Image Gallery' && f.value !== '' && f.value !== null)
    .map((f, i) => image(f.value, key, `${key}-g${i}`))
    .filter((block): block is Json => block !== undefined)
  const ingress = plain(clean(field(record, 'Ingress') ?? '', key))
  const body = blocks(paragraphs(field(record, 'Content') ?? ''), key)
  // Some posts repeat the ingress as the first paragraph, and the site shows
  // the ingress above the text, so the copy goes.
  if (ingress !== '' && blockText(body[0]) === ingress) {
    body.shift()
  }
  docs.push(
    withoutEmpty({
      _id: id(record),
      _type: 'nyhet',
      title: title(record),
      slug: slug(record),
      publiceringsdatum: `${String(record.rest.date_gmt)}Z`,
      ingress: ingress || undefined,
      huvudbild: image(record.rest.featured_media, key),
      brodtext: [...body, ...gallery],
    }),
  )
}

const years = new Map(dump.taxonomies.produktion_year.items.map((term) => [term.id, Number(term.name)]))
for (const record of dump.types.produktion.records) {
  const key = `wp-${record.rest.id}`
  const played = ((record.rest.produktion_year as number[] | undefined) ?? [])
    .map((term) => years.get(term))
    .filter((year): year is number => year !== undefined)
  const first = played.length ? Math.min(...played) : undefined
  const last = played.length ? Math.max(...played) : undefined
  docs.push(
    withoutEmpty({
      _id: id(record),
      _type: 'produktion',
      title: title(record),
      ar: first,
      tillAr: last !== first ? last : undefined,
      huvudbild: image(record.rest.featured_media, key),
      beskrivning: blocks(paragraphs(field(record, 'Content') ?? ''), key),
    }),
  )
}

// The board's order: the chair first, then the vice chair, then the rest.
const ORDER: Record<string, number> = {'ordförande': 1, 'v. ordförande': 2, 'vice ordförande': 2}
for (const record of dump.types.employee.records) {
  const key = `wp-${record.rest.id}`
  const role = field(record, null, 'text') ?? 'Ledamot'
  docs.push(
    withoutEmpty({
      _id: id(record),
      _type: 'styrelseledamot',
      namn: title(record),
      roll: role,
      ordning: ORDER[role.toLowerCase()] ?? 10,
      bild: image(record.rest.featured_media, key),
      ansvar: plain(clean(field(record, 'Description') ?? '', key)) || undefined,
    }),
  )
}

for (const record of dump.types.luddepris.records) {
  const key = `wp-${record.rest.id}`
  docs.push(
    withoutEmpty({
      _id: id(record),
      _type: 'luddepris',
      mottagare: title(record),
      ar: Number(field(record, 'year')),
      bild: image(record.rest.featured_media, key),
      motivering: blocks(paragraphs(field(record, 'Content') ?? ''), key),
    }),
  )
}

/**
 * A Bricks page as HTML, in the order the builder shows it. Query loops are
 * left out, and so is the block around one, with its heading: they listed
 * the board and the annual reports, which now come from their own type and
 * from the member documents.
 */
function bricksHtml(pageSlug: string): {html: string; images: unknown[]} {
  const elements = bricks[pageSlug]?.content ?? []
  const byId = new Map(elements.map((element) => [element.id, element]))
  const html: string[] = []
  const images: unknown[] = []
  const visit = (element: BricksElement): void => {
    const settings = (element.settings ?? {}) as Json
    const loops = (candidate: BricksElement | undefined) => Boolean((candidate?.settings as Json | undefined)?.hasLoop)
    if (
      loops(element) ||
      element.children.some((child) => loops(byId.get(child))) ||
      ['form', 'code', 'pagination', 'animated-typing', 'post-title'].includes(element.name)
    ) {
      return
    }
    const text = typeof settings.text === 'string' ? settings.text : ''
    const link = (settings.link as {url?: string} | undefined)?.url
    if (element.name === 'heading' && settings.tag !== 'h1') {
      html.push(settings.tag === 'h2' ? `<h2>${text}</h2>` : `<p><strong>${text}</strong></p>`)
    } else if (element.name === 'text' || element.name === 'text-basic' || element.name === 'button') {
      html.push(link ? `<p><a href="${link}">${text.trim()}</a></p>` : text)
    } else if (element.name === 'image') {
      images.push((settings.image as {id?: unknown} | undefined)?.id)
    }
    for (const child of element.children) {
      const next = byId.get(child)
      if (next) {
        visit(next)
      }
    }
  }
  elements.filter((element) => element.parent === 0 || element.parent === '0').forEach(visit)
  return {html: html.join('\n'), images}
}

// The fixed pages keep the ids they have in the dataset, so the import
// replaces their placeholder text instead of adding a second page per address.
const PAGES: Record<string, {id: string; title: string}> = {
  'om-foreningen': {id: 'sida-om', title: 'Om föreningen'},
  styrelsen: {id: 'sida-styrelsen', title: 'Styrelsen'},
  kontakt: {id: 'sida-kontakt', title: 'Kontakt'},
}
for (const [pageSlug, page] of Object.entries(PAGES)) {
  const {html, images} = bricksHtml(pageSlug)
  docs.push(
    withoutEmpty({
      _id: page.id,
      _type: 'sida',
      title: page.title,
      slug: {_type: 'slug', current: pageSlug},
      headerbild: images.map((imageId) => image(imageId, pageSlug)).find((found) => found !== undefined),
      innehall: blocks(html, page.id),
    }),
  )
}
for (const pageSlug of Object.keys(bricks).filter((pageSlug) => !(pageSlug in PAGES) && pageSlug !== 'partners')) {
  report.push(`page ${pageSlug}: left out, the new site has no such page`)
}

// The partners page is one paragraph per partner, with the partner's name in
// bold and its address as the last link. A paragraph that is nothing but a
// bold name is the page's heading. The paragraph on Jönköpings kommun has no
// bold name, and replaces the placeholder partner of that name.
for (const paragraph of new JSDOM(bricksHtml('partners').html).window.document.querySelectorAll('p')) {
  const name = paragraph.querySelector('strong')?.textContent?.trim()
  const links = [...paragraph.querySelectorAll('a')]
  const address = links.at(-1)?.getAttribute('href') ?? undefined
  links.forEach((link) => link.remove())
  const description = (paragraph.textContent ?? '').replace(/\s+/g, ' ').replace(/\s+([.,])/g, '$1').trim()
  const kommun = name === undefined && description.includes('Jönköpings kommun')
  if ((name === undefined && !kommun) || description === name) {
    continue
  }
  // "Smålands Musik & Teater …" says nothing beyond the name.
  const says = description.replace(name ?? '', '').replace(/[\s….]/g, '') !== ''
  docs.push(
    withoutEmpty({
      _id: kommun ? 'partner-kommun' : `wp-partner-${swedishSlug(name ?? '')}`,
      _type: 'partner',
      namn: kommun ? 'Jönköpings kommun' : name,
      webbplats: address,
      beskrivning: says ? description : undefined,
    }),
  )
}

docs.sort((a, b) => a._id.localeCompare(b._id))
writeFileSync(outFile, docs.map((doc) => JSON.stringify(doc)).join('\n') + '\n')

const counts = new Map<string, number>()
docs.forEach((doc) => counts.set(doc._type, (counts.get(doc._type) ?? 0) + 1))
console.log(`Wrote ${docs.length} documents to ${outFile}: ${[...counts].map(([t, n]) => `${n} ${t}`).join(', ')}`)
console.log(
  keepPrivateContacts
    ? 'Private addresses and numbers kept (--keep-private-contacts).'
    : `${heldBack} private addresses or numbers held back.`,
)
report.forEach((line) => console.log(`- ${line}`))
