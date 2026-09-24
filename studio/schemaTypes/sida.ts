import {defineField, defineType} from "sanity";

export default defineType({
    name: 'sida',
    title: 'sida',
    type: 'document',
    fields: [
        defineField({
            name: 'title',
            description: 'T.ex. "Om föreningen", "Styrelsen", "Produktioner", "Ludde-priser", "Kontakt"',
            type: 'string',
            validation: (Rule) => Rule.required(),
        }),
        defineField({
            name: 'slug',
            title: 'Slug(URL)',
            type: 'slug',
            options: {source: 'title', maxLength: 100},
            validation: (Rule) => Rule.required(),
        }),
        defineField({
            name: 'headerbild',
            title: 'Headerbild',
            type: 'image',
            options: {hotspot: true},
            fields: [
                {name: 'alt', title: 'Alt-text', type: 'string'},

            ],
        }),
        defineField({
            name: 'innehall',
            title: 'Innehåll',
            type: 'array',
            of: [
                {type: 'block'},
                {
                    type: 'image', options: {hotspot: true},
                    fields: [{name: 'alt', title: 'Alt-text', type: 'string'}],
                },
            ],
        }),
    ],
    preview: {
        select: {
            title: 'title',
            media: 'headerbild',
        },
        prepare({title, media}) {
            return {
                title, subtitle: 'Sida', media
            }
        },
    },
})

