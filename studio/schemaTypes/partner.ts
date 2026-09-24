import {defineField, defineType,TextOptions} from "sanity";

export default defineType({
    name: 'partner',
    title: ' Partner',
    type: 'document',
    fields: [
        defineField({
            name: 'namn',
            title: 'Namn',
            type: 'string',
            validation: (Rule) => Rule.required(),
        }),
        defineField({
            name: 'logotype',
            title: 'Logotype',
            type: 'image',
            options: {hotspot: true},
            validation: (Rule) => Rule.required(),
            fields: [{
                name: 'alt', title: 'Alt-text', type: 'string'
            },
            ],
        }),
        defineField({
            name: 'webplats',
            title: 'Länk till partners webplats',
            type: 'url',
        }),
        defineField({
            name: 'beskrivning',
            title: 'Kort beskrivning',
            type: 'text',
            options: {rows: 3} as TextOptions,
        }),
    ],
    preview: {
        select: {
            title: 'name',
            media: 'Logotype',
            webbplats: 'WebbPlats',
        },
        prepare({title, media, webbplats}
        ) {
            return {
                title,
                subtitle: webbplats ?? 'ingen webbplats angiven',
                media,
            }

        }
    },
})