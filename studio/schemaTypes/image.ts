import {defineField} from 'sanity'

/**
 * An image with the text a screen reader reads in its place, which R006
 * (WCAG 2.1 AA) requires. An image that only decorates may say so instead.
 */
export function imageField(name: string, title: string, description?: string) {
  return defineField({
    name,
    title,
    description,
    type: 'image',
    options: {hotspot: true},
    fields: [altField()],
  })
}

export function altField() {
  return defineField({
    name: 'alt',
    title: 'Alternativ text',
    description: 'Beskriv bilden för den som inte ser den, till exempel "Tre skådespelare på scenen".',
    type: 'string',
    validation: (rule) =>
      rule.custom((alt, context) => {
        const parent = context.parent as {asset?: unknown} | undefined
        return !parent?.asset || (alt && alt.trim()) ? true : 'Bilden behöver en alternativ text.'
      }),
  })
}
