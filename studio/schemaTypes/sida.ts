import {defineField, defineType} from 'sanity'
import {imageField} from './image'

/**
 * The fixed pages R004 names. The site has an address for each of these and
 * no others: ContentService.PAGES in the application.
 */
export const PAGES = [
  {title: 'Om föreningen', value: 'om-foreningen'},
  {title: 'Styrelsen', value: 'styrelsen'},
  {title: 'Produktioner', value: 'produktioner'},
  {title: 'Ludde-priser', value: 'ludde-priser'},
  {title: 'Kontakt', value: 'kontakt'},
]

export default defineType({
  name: 'sida',
  title: 'Sida',
  type: 'document',
  fields: [
    defineField({name: 'title', title: 'Rubrik', type: 'string', validation: (rule) => rule.required()}),
    defineField({
      name: 'slug',
      title: 'Vilken sida',
      type: 'slug',
      options: {
        source: 'title',
        slugify: (input: string) =>
          PAGES.find((page) => page.title.toLowerCase() === input.toLowerCase())?.value ?? input,
      },
      validation: (rule) =>
        rule.required().custom((slug) =>
          !slug?.current || PAGES.some((page) => page.value === slug.current)
            ? true
            : `Adressen måste vara en av ${PAGES.map((page) => page.value).join(', ')}.`,
        ),
    }),
    imageField('headerbild', 'Bild överst'),
    defineField({name: 'innehall', title: 'Innehåll', type: 'blockContent'}),
  ],
})
