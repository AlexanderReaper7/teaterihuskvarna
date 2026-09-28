import {defineArrayMember, defineType} from 'sanity'
import {altField} from './image'

/**
 * Rich text. The application turns it into HTML itself
 * (src/main/java/se/teaterihuskvarna/content/PortableText.java), and renders
 * only what is listed here. A style or mark added here has to be added there too.
 */
export const blockContent = defineType({
  name: 'blockContent',
  title: 'Text',
  type: 'array',
  of: [
    defineArrayMember({
      type: 'block',
      // H1 is the page's title, so the text starts at H2.
      styles: [
        {title: 'Normal', value: 'normal'},
        {title: 'Rubrik', value: 'h2'},
        {title: 'Underrubrik', value: 'h3'},
        {title: 'Mindre rubrik', value: 'h4'},
        {title: 'Citat', value: 'blockquote'},
      ],
      lists: [
        {title: 'Punktlista', value: 'bullet'},
        {title: 'Numrerad lista', value: 'number'},
      ],
      marks: {
        decorators: [
          {title: 'Fet', value: 'strong'},
          {title: 'Kursiv', value: 'em'},
        ],
        annotations: [
          {
            name: 'link',
            title: 'Länk',
            type: 'object',
            fields: [
              {
                name: 'href',
                title: 'Adress',
                type: 'url',
                validation: (rule) =>
                  rule.uri({scheme: ['http', 'https', 'mailto', 'tel'], allowRelative: true}),
              },
            ],
          },
        ],
      },
    }),
    defineArrayMember({
      type: 'image',
      options: {hotspot: true},
      fields: [altField()],
    }),
  ],
})
