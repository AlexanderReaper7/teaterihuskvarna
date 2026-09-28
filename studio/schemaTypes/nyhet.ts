import {defineField, defineType} from 'sanity'
import {imageField} from './image'

/** A news item: /nyheter (R003). One with a publishing date in the future stays hidden until then. */
export default defineType({
  name: 'nyhet',
  title: 'Nyhet',
  type: 'document',
  fields: [
    defineField({name: 'title', title: 'Rubrik', type: 'string', validation: (rule) => rule.required()}),
    defineField({
      name: 'slug',
      title: 'Adress',
      description: 'Sidans adress, /nyheter/<adress>.',
      type: 'slug',
      options: {source: 'title', maxLength: 96},
      validation: (rule) => rule.required(),
    }),
    defineField({
      name: 'publiceringsdatum',
      title: 'Publiceringsdatum',
      description: 'Nyheten syns på webbplatsen från och med denna tid.',
      type: 'datetime',
      initialValue: () => new Date().toISOString(),
      validation: (rule) => rule.required(),
    }),
    defineField({name: 'ingress', title: 'Ingress', type: 'text', rows: 3}),
    imageField('huvudbild', 'Huvudbild'),
    defineField({name: 'brodtext', title: 'Brödtext', type: 'blockContent'}),
    defineField({
      name: 'video',
      title: 'Video',
      description: 'En länk till YouTube eller Vimeo.',
      type: 'url',
      validation: (rule) => rule.uri({scheme: ['http', 'https']}),
    }),
  ],
  orderings: [
    {title: 'Publiceringsdatum', name: 'publicerad', by: [{field: 'publiceringsdatum', direction: 'desc'}]},
  ],
  preview: {
    select: {title: 'title', date: 'publiceringsdatum', media: 'huvudbild'},
    prepare: ({title, date, media}) => ({
      title,
      subtitle: date ? new Date(date).toLocaleDateString('sv-SE') : '',
      media,
    }),
  },
})
