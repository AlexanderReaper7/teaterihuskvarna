import {defineField, defineType} from 'sanity'
import {imageField} from './image'

/** A Ludde prize the association has awarded, listed on /ludde-priser (R004). */
export default defineType({
  name: 'luddepris',
  title: 'Ludde-pris',
  type: 'document',
  fields: [
    defineField({
      name: 'mottagare',
      title: 'Mottagare',
      description: 'Den person eller grupp som fick priset.',
      type: 'string',
      validation: (rule) => rule.required(),
    }),
    defineField({
      name: 'ar',
      title: 'År',
      type: 'number',
      validation: (rule) => rule.required().integer().min(1900).max(2100),
    }),
    imageField('bild', 'Bild'),
    defineField({name: 'motivering', title: 'Motivering', type: 'blockContent'}),
  ],
  orderings: [{title: 'År', name: 'ar', by: [{field: 'ar', direction: 'desc'}]}],
  preview: {
    select: {title: 'mottagare', ar: 'ar', media: 'bild'},
    prepare: ({title, ar, media}) => ({title, subtitle: ar ? String(ar) : '', media}),
  },
})
