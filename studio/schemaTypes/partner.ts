import {defineField, defineType} from 'sanity'
import {imageField} from './image'

/** A partner, listed on /partners (R004). */
export default defineType({
  name: 'partner',
  title: 'Partner',
  type: 'document',
  fields: [
    defineField({name: 'namn', title: 'Namn', type: 'string', validation: (rule) => rule.required()}),
    imageField('logotyp', 'Logotyp'),
    defineField({
      name: 'webbplats',
      title: 'Webbplats',
      type: 'url',
      validation: (rule) => rule.uri({scheme: ['http', 'https']}),
    }),
    defineField({name: 'beskrivning', title: 'Beskrivning', type: 'text', rows: 3}),
  ],
  preview: {select: {title: 'namn', subtitle: 'webbplats', media: 'logotyp'}},
})
