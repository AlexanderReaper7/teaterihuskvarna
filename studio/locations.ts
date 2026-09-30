import {defineLocations, type DocumentLocationResolvers} from 'sanity/presentation'

// Which pages of the site show a document, so the Presentation tool can open
// them. The paths are the ones ContentPageController maps.
export const locations: DocumentLocationResolvers = {
  evenemang: defineLocations({
    select: {title: 'title', slug: 'slug.current'},
    resolve: (doc) => ({
      locations: [
        {title: doc?.title || 'Evenemang', href: `/evenemang/${doc?.slug}`},
        {title: 'Kalender', href: '/kalender'},
        {title: 'Startsidan', href: '/'},
      ],
    }),
  }),
  nyhet: defineLocations({
    select: {title: 'title', slug: 'slug.current'},
    resolve: (doc) => ({
      locations: [
        {title: doc?.title || 'Nyhet', href: `/nyheter/${doc?.slug}`},
        {title: 'Nyheter', href: '/nyheter'},
      ],
    }),
  }),
  sida: defineLocations({
    select: {title: 'title', slug: 'slug.current'},
    resolve: (doc) => ({
      locations: [{title: doc?.title || 'Sida', href: `/${doc?.slug}`}],
    }),
  }),
  partner: defineLocations({
    locations: [{title: 'Partners', href: '/partners'}],
  }),
  serie: defineLocations({
    select: {title: 'title', slug: 'slug.current'},
    resolve: (doc) => ({
      locations: [{title: doc?.title || 'Serie', href: `/kalender?serie=${doc?.slug}`}],
    }),
  }),
}
