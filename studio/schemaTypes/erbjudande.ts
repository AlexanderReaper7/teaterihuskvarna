import {defineField, defineType} from "sanity";

export default defineType({
    name: 'erbjudande',
    title: 'Erbjudande',
    type: 'document',
    fields: [
        defineField({
            name: 'titel',
            title: 'Titel',
            type: 'string',
            validation: (Rule) => Rule.required(),
        }),
        defineType({
            name: 'bild',
            title: 'Bild',
            type: 'image',
            options: {hotspot: true},
            fields: [
                {name: 'alt', title: 'Alt-text', type: 'string'},
            ],
        }),
        defineField({
            name: 'beskrivning',
            title: 'Beskrivning',
            type: 'array',
            of: [{type: 'block'}],
        }),
        defineField({
            name: 'antalPlatser',
            title: 'Antal platser totalt',
            description: 'Platser kvar räknas ut i appen utifrån antal anmälda mdedlemdatabasen, inte här',
            type: 'number',
            validation: (Rule) => Rule.min(0),
        }),
        defineField({
            name: 'aktiv',
            title: 'Aktiv (visas for medlemmar)',
            type: 'boolean',
            initialValue: true,
        }),
    ],
    preview: {
        select: {
            title: 'titel',
            media: 'bild,',
            aktiv: 'aktiv',
            sistaAnmalningsdatum: 'sistaAnmalningsdatum',
        },
        prepare({title, media, aktiv, sistaAnmalningsdatum}) {
            const status = aktiv ? 'Aktive' : 'InAktive'
            const datum = sistaAnmalningsdatum
                ? `Sista dag:${sistaAnmalningsdatum} `
                : ''
            return {
                title,
                subtitle: `${status}${datum}`,
                media,
            }
        },

    },
})