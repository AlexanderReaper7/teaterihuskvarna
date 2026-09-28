import {defineConfig} from 'sanity'
import {presentationTool} from 'sanity/presentation'
import {structureTool} from 'sanity/structure'
import {visionTool} from '@sanity/vision'
import {dataset, previewOrigin, projectId} from './env'
import {locations} from './locations'
import {schemaTypes} from './schemaTypes'

export default defineConfig({
  name: 'default',
  title: 'Teater i Huskvarna',
  projectId,
  dataset,
  plugins: [
    structureTool(),
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
  ],
  schema: {types: schemaTypes},
})
