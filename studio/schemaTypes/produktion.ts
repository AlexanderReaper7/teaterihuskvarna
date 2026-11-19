import {defineField, defineType} from 'sanity'
import {imageField} from './image'

/** A production the association has played, listed on /produktioner (R004). */
export default defineType({
  name: 'produktion',
  title: 'Produktion',
  type: 'document',
  fields: [
    defineField({name: 'title', title: 'Titel', type: 'string', validation: (rule) => rule.required()}),
    defineField({
      name: 'ar',
      title: 'År',
      description: 'Året då produktionen hade premiär.',
      type: 'number',
      validation: (rule) => rule.integer().min(1900).max(2100),
    }),
    defineField({
      name: 'tillAr',
      title: 'Till år',
      description: 'Det sista året den spelades. Lämna tomt om den spelades ett år.',
      type: 'number',
      validation: (rule) =>
        rule
          .integer()
          .custom((tillAr, context) => {
            const ar = (context.parent as {ar?: number} | undefined)?.ar
            return tillAr === undefined || ar === undefined || tillAr >= ar
              ? true
              : 'Till år kan inte vara före År.'
          }),
    }),
    imageField('huvudbild', 'Bild'),
    defineField({name: 'beskrivning', title: 'Beskrivning', type: 'blockContent'}),
  ],
  orderings: [{title: 'År', name: 'ar', by: [{field: 'ar', direction: 'desc'}]}],
  preview: {
    select: {title: 'title', ar: 'ar', tillAr: 'tillAr', media: 'huvudbild'},
    prepare: ({title, ar, tillAr, media}) => ({
      title,
      subtitle: ar ? (tillAr && tillAr !== ar ? `${ar}–${tillAr}` : String(ar)) : '',
      media,
    }),
  },
})
