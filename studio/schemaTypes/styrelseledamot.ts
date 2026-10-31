import {defineField, defineType} from 'sanity'
import {imageField} from './image'

/**
 * A member of the board, listed on /styrelsen (R004). It has no contact
 * fields: a private address or number must not appear on the public site.
 */
export default defineType({
  name: 'styrelseledamot',
  title: 'Styrelseledamot',
  type: 'document',
  fields: [
    defineField({name: 'namn', title: 'Namn', type: 'string', validation: (rule) => rule.required()}),
    defineField({
      name: 'roll',
      title: 'Roll',
      description: 'Till exempel Ordförande, Kassör eller Ledamot.',
      type: 'string',
      validation: (rule) => rule.required(),
    }),
    defineField({
      name: 'ordning',
      title: 'Ordning',
      description: 'Lägre tal visas först på sidan Styrelsen. Ordföranden brukar ha 1.',
      type: 'number',
      initialValue: 10,
      validation: (rule) => rule.integer(),
    }),
    imageField('bild', 'Foto'),
    defineField({
      name: 'ansvar',
      title: 'Ansvarsområde',
      description: 'En kort rad om vad personen gör i styrelsen.',
      type: 'text',
      rows: 2,
    }),
  ],
  orderings: [{title: 'Ordning', name: 'ordning', by: [{field: 'ordning', direction: 'asc'}]}],
  preview: {select: {title: 'namn', subtitle: 'roll', media: 'bild'}},
})
