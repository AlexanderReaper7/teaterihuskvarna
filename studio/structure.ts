import type {StructureResolver} from 'sanity/structure'

const API_VERSION = 'v2025-02-19'

// The content list, ordered by what editors look for: the next events first,
// the newest news first, and the rest by name. An event without a date yet is
// a draft being written, so it is listed with the upcoming ones.
export const structure: StructureResolver = (S) =>
  S.list()
    .title('Innehåll')
    .items([
      S.listItem()
        .title('Evenemang')
        .schemaType('evenemang')
        .child(
          S.list()
            .title('Evenemang')
            .items([
              S.listItem()
                .title('Kommande')
                .schemaType('evenemang')
                .child(
                  S.documentTypeList('evenemang')
                    .title('Kommande evenemang')
                    .apiVersion(API_VERSION)
                    .filter('_type == "evenemang" && (!defined(datumTid) || dateTime(datumTid) >= dateTime(now()))')
                    .defaultOrdering([{field: 'datumTid', direction: 'asc'}]),
                ),
              S.listItem()
                .title('Tidigare')
                .schemaType('evenemang')
                .child(
                  S.documentTypeList('evenemang')
                    .title('Tidigare evenemang')
                    .apiVersion(API_VERSION)
                    .filter('_type == "evenemang" && dateTime(datumTid) < dateTime(now())')
                    .defaultOrdering([{field: 'datumTid', direction: 'desc'}]),
                ),
            ]),
        ),
      S.listItem()
        .title('Nyheter')
        .schemaType('nyhet')
        .child(
          S.documentTypeList('nyhet')
            .title('Nyheter')
            .defaultOrdering([{field: 'publiceringsdatum', direction: 'desc'}]),
        ),
      S.listItem()
        .title('Serier')
        .schemaType('serie')
        .child(S.documentTypeList('serie').title('Serier').defaultOrdering([{field: 'title', direction: 'asc'}])),
      S.listItem()
        .title('Sidor')
        .schemaType('sida')
        .child(S.documentTypeList('sida').title('Sidor').defaultOrdering([{field: 'title', direction: 'asc'}])),
      S.listItem()
        .title('Partners')
        .schemaType('partner')
        .child(S.documentTypeList('partner').title('Partners').defaultOrdering([{field: 'namn', direction: 'asc'}])),
    ])
