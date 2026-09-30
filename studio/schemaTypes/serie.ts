import {defineField, defineType} from 'sanity'

/** A series of events, such as Kaffe med drömmar. The calendar filters by it (R002). */
export default defineType({
  name: 'serie',
  title: 'Serie',
  type: 'document',
  fields: [
    defineField({name: 'title', title: 'Namn', type: 'string', validation: (rule) => rule.required()}),
    defineField({
      name: 'slug',
      title: 'Adress',
      description: 'Används i kalenderns filter, /kalender?serie=<adress>.',
      type: 'slug',
      options: {source: 'title', maxLength: 96},
      validation: (rule) => rule.required(),
    }),
  ],
})
