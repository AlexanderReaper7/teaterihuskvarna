import {defineField, defineType} from 'sanity'

export default defineType({
    name: 'evenemang',
    title: 'Evenemang',
    type: 'document',
    fields: [
        defineField({
            name:'title',
            title:'Titel',
            type:'string',
            validation:(Rule)=> Rule.required(),
        }),
        defineField({
            name:'slug',
            title:'slug(URL)',
            type:'slug',
            options:{source:'title', maxLength: 1000},
            validation:(Rule)=> Rule.required(),
        }),
        defineField({
            name:'serie',
            title:'Serie/Kategori',
            description:'T.ex. "Kaffe med drömmar", "Teaterträdgården Smedbyn", "Alf Henrikson-dagen"',
            type:'string',
            options:{
                list:[
                    {title:'Kaffe med drömmar', value:'kaffe-med-drommar'},
                    {title:'Teaterträdgården Smedbyn', value:'teatertradgarden-Smedbyn'},
                    {title:'Alf Henriksson-dagen', value:'alf-henriksson-dagen'},
                    {title:'Övrigt', value:'ovrigt'},
                ],
            },
        }),
        defineField({
            name:'datumTid',
            title:'Datum och tid',
            type:'datetime',
            validation:(Rule)=> Rule.required(),
        }),
        defineField({
            name:'location',
            title:'Plats',
            type:'string',
        }),
        //--- Bild Material---
        defineField({
            name:'huvudbild',
            title:'Huvudbild',
            description:'Visas överst på evenemangssida och i kalender',
            type:'image',
            options:{hotspot:true},
            validation:(Rule)=> Rule.required(),
        }),
        defineField({
            name:'bildgalleri',
            title:'Bildgalleri',
            description:'fler bilder,  t.ex. från repetitioner eller tidigare föreställningar',
            type:'array',
            of:[
                {
                    type:'image',
                    options:{hotspot:true},
                    fields:[{
                        name:'alt', title:'Alt-text', type:'string'
                    }]
                },
            ],
        }),
//videoMaterial?
    ],
preview:{
        select:{
            title:'titel',
            datumTid:'datumTid',
            media:'huvudbild'
        },
    prepare({ title,datumTid, media}){
            return{
                title,
                subtitle: datumTid? new Date(datumTid).toLocaleString('sv-SE'):'',
                media,
            }
    },
},
})