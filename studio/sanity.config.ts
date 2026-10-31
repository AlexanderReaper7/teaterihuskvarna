import {svSELocale} from '@sanity/locale-sv-se'
import {defineConfig} from 'sanity'
import {presentationTool} from 'sanity/presentation'
import {structureTool} from 'sanity/structure'
import {visionTool} from '@sanity/vision'
import {dataset, previewOrigin, projectId} from './env'
import {locations} from './locations'
import {schemaTypes} from './schemaTypes'
import {structure} from './structure'

export default defineConfig({
  name: 'default',
  title: 'Teater i Huskvarna',
  projectId,
  dataset,
  plugins: [
    structureTool({structure}),
    // R009: the site with drafts, beside the editor. The application checks
    // the secret the tool writes and sets its own preview cookie:
    // src/main/java/se/teaterihuskvarna/web/PreviewController.java.
    presentationTool({
      previewUrl: {
        origin: previewOrigin,
        previewMode: {
          enable: '/forhandsgranska/start',
          disable: '/forhandsgranska/avsluta',
        },
      },
      resolve: {locations},
    }),
    visionTool({defaultApiVersion: 'v2025-02-19', defaultDataset: dataset}),
    // The editors are Swedish volunteers; the Studio's own buttons and
    // messages follow the fields into Swedish.
    svSELocale(),
  ],
  // Vision runs GROQ queries against the dataset. It is a developer's tool,
  // and only the project's administrators see it.
  tools: (tools, {currentUser}) =>
    currentUser?.roles.some((role) => role.name === 'administrator')
      ? tools
      : tools.filter((tool) => tool.name !== 'vision'),
  schema: {types: schemaTypes},
})
