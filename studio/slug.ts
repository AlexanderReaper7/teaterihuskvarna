// An address made from a title, written the way Swedish addresses usually
// are: å and ä become a, ö becomes o, other accents are dropped, and anything
// that is not a letter or a digit becomes one hyphen. Sanity's own "Generera"
// follows German and writes "Föreställning" as foerestaellning.
export function swedishSlug(input: string, maxLength = 96): string {
  return input
    .toLowerCase()
    .replace(/[åä]/g, 'a')
    .replace(/ö/g, 'o')
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .replace(/[^a-z0-9]+/g, '-')
    .slice(0, maxLength)
    .replace(/^-+|-+$/g, '')
}
