// The Studio reads SANITY_STUDIO_* variables from studio/.env, or from the
// environment of `sanity dev`, `sanity build` and `sanity deploy`.
// studio/README.md lists them.

/** The dataset to edit: `dev` for testing, `production` for the real site. */
export const dataset = process.env.SANITY_STUDIO_DATASET || 'dev'

/** The site the Presentation tool shows, such as https://teaterihuskvarna.se. */
export const previewOrigin = process.env.SANITY_STUDIO_PREVIEW_ORIGIN || 'http://localhost:8000'

export const projectId = 'gk5ur3tb'
