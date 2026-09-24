import {defineField, defineType, TextOptions} from "sanity";

export default defineType({
    name: 'nyhet',
    title: 'Nyhet',
    type: 'document',
    fields: [
        defineField({
            name: 'title',
            title: 'Titel',
            type: 'string',
            validation: (Rule) => Rule.required(),
        }),
        defineField({
            name: 'slug',
            title: 'slug(URL)',
            type: 'slug',
            options: {source: 'title', maxLength: 100},
            validation: (Rule) => Rule.required(),
        }),
        defineField({
            name: 'publiceringsdatum',
            title: 'Publiceringsdatum',
            type: 'datetime',
            initialValue: () => new Date().toISOString(),
        }),
        defineField({
            name: 'ingress',
            title: 'Ingress',
            description: 'Kort sammanfattning, visas i nyhetslistan',
            type: 'text',
            options: {rows: 3} as TextOptions,
        }),
        defineField({
            name: 'video',
            title: 'Video',
            description: 'Länk till YouTube eller Vimoe, om nyheten ska ha ett inbäddat klipp',
            type: 'url',
        }),
        defineField({
            name: 'brodtext',
            title: 'Brödtext',
            type: 'array',
            of: [
                {type: 'block'}, {type: 'image', options: {hotspot: true}},
            ],
        }),
    ],
    preview: {
        select: {
            title: 'title',
            publiceringsdatum: 'publiceringsdatum',
            media: 'bild',
        },
        prepare({title, publiceringsdatum, media}) {
            return {
                title,
                subtitle: publiceringsdatum
                    ? new Date(publiceringsdatum).toLocaleString('se-SE')
                    : 'Inget datum',
                media,
            }
        },
    },
})