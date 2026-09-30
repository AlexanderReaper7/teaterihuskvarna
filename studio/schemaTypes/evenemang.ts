import {defineField, defineType} from 'sanity'
import {imageField} from './image'

/** An event: the start page (R001), the calendar (R002) and /evenemang/<adress>. */
export default defineType({
  name: 'evenemang',
  title: 'Evenemang',
  type: 'document',
  fields: [
    defineField({name: 'title', title: 'Titel', type: 'string', validation: (rule) => rule.required()}),
    defineField({
      name: 'slug',
      title: 'Adress',
      description: 'Sidans adress, /evenemang/<adress>.',
      type: 'slug',
      options: {source: 'title', maxLength: 96},
      validation: (rule) => rule.required(),
    }),
    defineField({
      name: 'datumTid',
      title: 'Datum och tid',
      type: 'datetime',
      options: {timeStep: 15},
      validation: (rule) => rule.required(),
    }),
    defineField({name: 'plats', title: 'Plats', type: 'string'}),
    defineField({
      name: 'serie',
      title: 'Serie',
      description: 'Till exempel Kaffe med drömmar. Lämna tomt för ett fristående evenemang.',
      type: 'reference',
      to: [{type: 'serie'}],
    }),
    defineField({
      name: 'ingress',
      title: 'Ingress',
      description: 'En eller två meningar som visas i kalendern och på startsidan.',
      type: 'text',
      rows: 3,
    }),
    imageField('huvudbild', 'Huvudbild', 'Visas överst på evenemangets sida och i kalendern.'),
    defineField({name: 'beskrivning', title: 'Beskrivning', type: 'blockContent'}),
    defineField({
      name: 'bildgalleri',
      title: 'Bildgalleri',
      description: 'Fler bilder, till exempel från repetitioner.',
      type: 'array',
      of: [imageField('bild', 'Bild')],
    }),
    defineField({
      name: 'biljettlank',
      title: 'Länk till biljetter',
      type: 'url',
      validation: (rule) => rule.uri({scheme: ['http', 'https']}),
    }),
  ],
  orderings: [{title: 'Datum', name: 'datum', by: [{field: 'datumTid', direction: 'desc'}]}],
  preview: {
    select: {title: 'title', datumTid: 'datumTid', media: 'huvudbild'},
    prepare: ({title, datumTid, media}) => ({
      title,
      subtitle: datumTid ? new Date(datumTid).toLocaleString('sv-SE') : '',
      media,
    }),
  },
})
